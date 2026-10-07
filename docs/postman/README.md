# Postman: LUDO-T coordinator server

Two files:

- `LUDO-T.postman_collection.json`: the requests, with test scripts.
- `LUDO-T.postman_environment.json`: the variables `baseUrl`, `gameId`, `colour`, `version`, `turnId`, `hash`, `decisionId` and a few helpers. The scripts fill them in from the server's replies.

## Import

1. Start a server: `java -jar ludo-server/target/ludo-server.jar --port=8080` (or `--turn-delay=0` for speed).
2. In Postman: **Import** → drop both files.
3. Select the **LUDO-T local** environment (top right). Change `baseUrl` if the server runs on another PC, e.g. `http://192.168.1.20:8080`.

## The folders

| Folder | Requests | What the tests check |
|---|---|---|
| **Lifecycle** | Health, Create game, List games, Get game, Get state | 200/201. `Create game` stores `gameId`. `Get game` checks the queue fields `queueCapacity`, `queueDepth`, `peakQueueDepth`, `accepted`, `rejected`, `refused` and `otherErrors`. |
| **Player actions** | Join Red/Green/Yellow/Blue, Get state (whose turn), Roll, Get state (after the roll), Decision, Get state (to acknowledge), Ack | Joins are 200. `Get state` reads `openRequest` and stores `colour`, `turnId`, `version`, then `decisionId` and a candidate piece. The ROLL is accepted (200). The DECISION is 200 when one was open (some rolls need none: then 409). An ACK of the current version with its hash is 200, or 409 "stale ACK" if the game has already moved on. |
| **Error cases** | makes its own game and joins all four, then: Stale version → 409, Roll with a fixed requestId → 200, Same requestId again → stored reply, Unknown game → 404, Bad endCondition → 400 | The status codes, and that the repeated requestId gets **exactly the same body** as the first time, although the game has moved on. The server does not apply it twice (idempotency). |

Postman has no event stream, so it learns whose turn it is from `openRequest` in `GET /games/{id}/state`. A game played only from Postman pauses after `--move-timeout` (10 s) without an answer. After 30 s more the server plays that colour itself.

## Run a folder with the Collection Runner

1. Right-click a folder → **Run folder** (or Collection → **Run**).
2. Keep the request order. Run **Lifecycle** before **Player actions**: Lifecycle creates the game that Player actions joins. **Error cases** makes its own game.
3. **Run**. Each request shows its tests as passed or failed. The `Get state (whose turn)` requests ask again by themselves (at most 20 times) until the game waits for a ROLL. Right after the fourth JOIN, the game needs a moment to start.

To see what the queue did, open **Lifecycle → Get game (queue statistics)** after a run. The Postman console (View → Show Postman Console) prints `queue x/64, peak p, accepted a, rejected r, refused n`.

## Performance test (virtual users) while the GUI watches

This shows the server queueing simultaneous requests from many clients while a game is played and watched:

1. Start a game that is played and shown: run `scripts\start-demo.bat` (game 1 on port 8080, a spectator window and four headless players). Or use `scripts\run-testclients.bat` and its burst game on port 8090.
2. In the environment set `gameId` to that game (e.g. `1`).
3. In Postman: Collection → **Run** → **Performance** tab. Choose only **Lifecycle → Get state** and **Player actions → Roll** (and **Get state (whose turn)** before Roll, so each virtual user reads the current turn first).
4. For example 20 virtual users, 2 minutes, fixed load profile → **Run**.
5. Watch at the same time:
   - the **game window**: the game goes on normally; nobody's board jumps or goes out of step.
   - the **server console**: lines `queue=N ROLL ...` with N above 0 are requests waiting in the game's queue. Most ROLLs get `409` (not that colour's turn, or stale turnId/version). When one of them is the current turn's valid ROLL, it may be accepted instead of the real player's, and then the real player's ROLL gets 409. At most one ROLL per turn is ever accepted.
   - Postman's results: response times, and the share of 200 / 409. A `503` means the game's queue was full at that moment. The test clients retry 503; Postman counts it as an error.
6. Afterwards, **Get game (queue statistics)** shows `peakQueueDepth` (how many requests waited at once) and the `accepted`, `rejected` and `refused` counts for the whole run.

GET /state never goes through the queue: it reads the last published STATE, so it stays fast however busy the game is.
To see 503s on purpose, start the server with a small queue, e.g. `--queue-capacity=4`.
