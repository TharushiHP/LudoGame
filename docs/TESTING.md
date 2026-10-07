# Testing concurrency: the test clients

This document explains how the project proves both concurrency criteria of Assignment 2:

| Rubric / brief | Evidence |
|---|---|
| **Server side (70-100):** "the server will handle multiple requests from multiple clients, when they are sent simultaneously by fast, automatic clients, by putting them in a queue for processing as soon as possible." | The **burst** and **play** scenarios. `peakQueueDepth > 1` in `GET /games/{id}` and `queue=N` (N > 0) in the server log show requests waiting in a game's queue. Every request still gets a correct answer. With a small queue (`--queue-capacity=4`) the server answers `503` instead of blocking, and the clients retry until it takes the request. |
| **Client side (70-100):** "two test clients simultaneously send asynchronous requests in rapid succession." | The **burst** scenario: two (up to four) test clients, each with its own `HttpClient`, thread and event stream, fire waves of requests with `HttpClient.sendAsync` at the same moment. A whole wave is launched before its first reply arrives. **create** does the same with `POST /games`. |
| **Brief, step 4:** "Write two or more test clients that will simultaneously send asynchronous requests in rapid succession to the server. If the server cannot handle the requests immediately, it should put them in a queue for processing as soon as possible." | All of the above, run automatically by `ScenarioTest` (in-process server) and by `scripts\run-testclients.bat` (separate server, summaries saved for the report). |

## How the server queues (recap)

Each game has **one** game thread `game-<id>`. Only that thread ever touches the board. HTTP requests arrive on a pool of 16 `http-worker` threads. A worker never changes the game: it puts the request on the game's bounded `ArrayBlockingQueue` (64 by default) and waits for the game thread's reply. The game thread takes one command at a time, in arrival order, so every request is applied in one total order (see [THREADS.md](THREADS.md)).

- When several requests arrive together, they **wait in the queue** and are processed as soon as the game thread is free. The queue's high-water mark (`peakQueueDepth`) records how many waited at once.
- When the queue is **full**, the request is refused at once with **503** (back-pressure, never a blocked or lost request). The client's `RetryPolicy` sends the *same* request again after 250, 500, 1000, 2000 ms. It is the same requestId, so a retry can never be applied twice.
- `GET /state` and `GET /games/{id}` never use the queue: they read values the game thread has published.

## The jar and its options

```
java -jar ludo-testclients/target/ludo-testclients.jar --server=http://localhost:8090 --scenario=burst
```

| Option | Default | Meaning |
|---|---|---|
| `--server` | `http://localhost:8080` | Any server URL, so the clients can run on other PCs |
| `--scenario` | `all` | `play`, `burst`, `create` or `all` (burst, play, create) |
| `--games` | 3 | play: games at the same time (4 clients each) |
| `--clients` | 2 | burst: test clients (1-4, one seat each); create: clients creating games |
| `--requests` | 200 | burst: requests per burst client |
| `--wave-size` | 50 | burst: requests launched together in one wave |
| `--creates` | 25 | create: games each client creates |
| `--turn-delay` | 0 | turn delay of the burst (and create) games |
| `--seed` | 1 | seed of the first game (play: seed, seed+1, ...) |
| `--timeout` | 300000 | ms a scenario may take |
| `--out-dir` | `logs` | where the summaries are saved |
| `--watch` | – | burst: path of `ludo-client.jar`; opens a spectator window on the burst game |

The exit code is 0 only if every check of every scenario passed.

## The scenarios

### play: several full games at once

1. N games are created at the same time (turn delay 0, seeds `seed`, `seed+1`, ...).
2. 4·N automatic clients start at the same time. Each is the **real thick client** (`ClientSession`: event stream, controller, the colour's strategy) without a window, with its own `HttpClient`.
3. Every JOIN, ROLL, DECISION and ACK is asynchronous. A client sends a ROLL or DECISION only when the server asks for it (the protocol's lockstep), but the 4·N clients do so independently and at full speed.

| Check | Proves |
|---|---|
| all N games created (201) | concurrent `POST /games` work |
| every game reached GAME_OVER | no deadlock or lost request under load |
| every ACK hash matched | every client saw exactly the server's state, every time |
| all 4 clients of each game and GET /state end with the same hash | the final state is identical on the server and on all four clients; the checker also recomputes the hash from GET /state's snapshot |
| no unexpected client warnings | no request was refused that should have been accepted |
| every ACK accepted (200) | the ack barrier worked for every STATE |
| no lost request and no server error | every request got 2xx/409/503, never 5xx or no answer |

### burst: two or more clients firing at one game

1. One game is created and played by four automatic clients, as in *play*.
2. K **burst clients** (default 2) each have a seat (Red, Green, ...), their own `HttpClient`, their own spectator event stream and their own daemon thread `burst-client-N`.
3. Whenever a burst client's stream shows a ROLL_REQUEST for a burst seat, it launches a **wave** of requests in a tight loop with `sendAsync`. Every burst client sees the same event at about the same moment, so they fire **simultaneously**. Each wave of ten is:
   - 3 ROLLs for its own seat with the current turnId and version (fresh requestIds). Valid when it is its seat's turn, so they race the real player's ROLL.
   - 2 exact copies (same requestId) of the first ROLL.
   - 2 ACKs of the current version, with the hash it computed itself.
   - 1 exact copy of the first ACK.
   - 2 stale ROLLs (previous turnId and version).

| Check | Proves |
|---|---|
| every burst request launched / answered with 200, 409 or 503 | nothing was lost, even with hundreds of requests in flight at once |
| a repeated requestId gets the identical stored reply | idempotency under concurrency: the copies may arrive in any order; the first one processed decides, and the others get exactly its stored reply. Only copies that reached the game are compared; a copy still refused with 503 after all retries never entered the queue and is counted in the notes instead |
| at most one ROLL accepted per turnId | the race between the real player and the burst clients is decided once: the game thread is the only one that applies a ROLL |
| every stale ROLL rejected | version/turnId checks still hold under load |
| the game checks of *play* | the game played on consistently for all four players. A player's own ROLL may get 409 when a burst ROLL won its turn; that one warning is allowed |
| requests waited in the server's queue (peakQueueDepth > 1) | **the server queued simultaneous requests** and processed them one after another |

### create: many clients creating games at once

K clients (threads `create-client-N`, own `HttpClient` each) wait on one start latch, then each launches M `POST /games` without waiting. Checks: K·M answers 201 and K·M **distinct** game ids (the registry's `AtomicLong` and `ConcurrentHashMap`). The new games wait for players until the server stops.

## Reading a summary

Each run prints a summary and saves it as `logs/testclients-<scenario>-<yyyyMMdd-HHmmss>.txt`:

```
Requests: burst clients (2 HttpClients)
type       sent attempts    2xx    409  503(1st)  retries  final503  other   min ms   avg ms   p95 ms   max ms
ack         120      120    ...
roll        280      280    ...
total       400      400    ...
```

| Column | Meaning |
|---|---|
| sent | requests that got their final answer |
| attempts | HTTP attempts, retries included (attempts − sent = retries) |
| 2xx / 409 | final answers: accepted / rejected as stale, duplicate-of-rejected or not your turn |
| 503(1st) | first attempts refused because the game's queue was full |
| retries | attempts after a 503 (or a network error); each one resent the same request |
| final503 | requests still 503 after all retries (should be 0) |
| other | anything else (5xx, no answer); must be 0 |
| min/avg/p95/max ms | time from the first attempt to the final answer, retry waits included |

**Server queue** lists, per game, what `GET /games/{id}` reported: `queueCapacity`, `peakQueueDepth` (the most commands that waited at once), and `accepted`/`rejected`/`refused`/`otherErrors` as the server counted them. These counts include the players' requests, so they are larger than the burst clients' numbers.

**Notes** say how many waves were fully launched before their first reply arrived (the "rapid succession" evidence), and the 503/retry totals.

**Checks** are PASS/FAIL with the numbers behind them, and **Result** is PASS only if all passed.

**The overload run.** With `--queue-capacity=4` on the server and `--clients=4 --requests=300 --wave-size=50`, 200 requests land on a game whose queue holds 4.
- **503(1st)** counts the requests the server pushed back at once instead of queueing them.
- **retries** counts how often clients sent the same request again. Expect several retries per 503: the waits are 250, 500, 1000, 2000 and 2000 ms.
- **final503** counts requests that were still refused after all six attempts (about 5.75 s). The game never saw them, and the notes list how many were duplicate copies ("N duplicate copies gave up after all retries; never applied").

Whether anything gives up depends on the machine's speed, so the automated test asserts only that 503s and retries happen and that every check passes.

This run first revealed that the server's reply memory, then the last 1000 replies, forgot replies within 1–2 s under this load. A late retry got 409 instead of its stored 200. Replies are now kept for 30 s, above the retry window (see CHANGES_FROM_A1.md, Task 11).

On a slow machine the overload can also make a *player's* own request give up. In one run Red's DECISION did, so the game paused after the move timeout and the server took Red over 30 s later. The burst clients then stopped waiting for the next turn, and the checks "every burst request launched" and "no unexpected client warnings" failed, while every consistency check (hashes, one ROLL per turn, stored replies) still passed.

In the **server log** (`logs\server-load.log`), every command the game thread takes is logged as `queue=N ROLL RED req=... -> 409 ...`. N is the number of commands still waiting behind it, so `queue=3` means three more requests were queued at that moment.

## How to run it

**Automated (part of the build):** `./mvnw test -pl ludo-testclients -am`. `ScenarioTest` starts a real server in-process on a free port (turn delay 0, no next game) and runs:

- play with 3 games;
- burst with 2 clients × 200 requests, also asserting `peakQueueDepth > 1`, identical replies for repeated requestIds, at most one ROLL per turn and `queue=N` (N > 0) in the server log;
- burst against a server with `--queue-capacity=4`: there are first-attempt 503s, every one is retried (no request ends with 503), the server counted its refusals, and the game still ends consistently;
- burst under heavy overload (queue of 4, 4 clients × waves of 50): first-attempt 503s and retries happen, and every check passes;
- create with 4 clients × 25 games: 100 unique ids.

**For the report and the demo:** `scripts\run-testclients.bat`. It builds the jars if they are missing. It starts a separate load server on port 8090 (`--turn-delay=0 --rematch-delay=0`, own window, log in `logs\server-load.log`). Then it runs burst (with a spectator window, turn delay 150 ms and waves of 10, so it can be watched), play with 4 games, and create with 4 × 25. It lists the saved summaries at the end.

**On several PCs:** start the server on one PC (`--port=8090 --turn-delay=0 --rematch-delay=0`), then run the jar on two or more other PCs at the same time, e.g. `--scenario=play --server=http://<server-ip>:8090` on each.

**Back-pressure demo:** start the server with `--queue-capacity=4`, then run `--scenario=burst`. The summary shows first-attempt 503s and their retries, and `refused` > 0 on the server, with every check still passing.

**Postman:** see [postman/README.md](postman/README.md) (Collection Runner and performance test with virtual users).
