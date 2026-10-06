# Threads of the coordinator server

Every thread the server runs, whether it is a daemon, and why. A JVM exits when only daemon threads are left. So a **non-daemon** thread is used for work that must not be cut off halfway. A **daemon** thread is used for housekeeping that may simply stop when the JVM exits.

The flags below were checked in a running server with `Thread.getAllStackTraces()`. `GameSessionTest` also asserts that `game-<id>` is non-daemon.

| Thread | Created by | Daemon? | Why | How it stops |
|---|---|---|---|---|
| `main` | JVM | no | Parses the options, starts `LudoServer`, registers the shutdown hook, then returns. | Returns straight after start-up. The JVM stays alive because of the non-daemon threads below. |
| `HTTP-Dispatcher` | JDK `HttpServer.start()` | no (inherited from `main`) | Accepts connections and hands each request to the worker pool. While it runs, the server is up. | `HttpServer.stop(1)` in `LudoHttpServer.stop()`. |
| `idle-timeout-task` | JDK `HttpServer` | yes | The JDK's timer that closes idle keep-alive connections. Housekeeping only. | Ends with the JVM or `HttpServer.stop`. |
| `http-worker-N` (fixed pool, 16) | `LudoHttpServer` (`NamedThreadFactory`) | no | Runs the handlers: parses JSON, puts a command on the game's queue and waits up to 5 s for the reply. Non-daemon, so a request in progress is answered rather than cut off. A **fixed** pool caps how many requests run at once; the bounded game queues then push back with 503. | `workers.shutdown()` + `awaitTermination(5 s)` after the HTTP server has stopped. |
| `game-<id>` (one per game) | `GameSession` | **no** | **The only thread that ever touches that game's `Game`** (thread confinement). It consumes the command queue, runs `Game.run()`, the ack barrier and pacing. Non-daemon, because the JVM must not exit in the middle of a game and leave clients waiting for a GAME_OVER that never comes. | With `--rematch-delay` on, it plays the session's games **one after another** (the next game is a new `Game` built on this same thread after the delay, which it spends polling its queue, never sleeping), so a rematch adds no thread. Ends by itself after the last GAME_OVER (rematch off: after the first). On shutdown, `GameSession.shutdown` interrupts it: `Game.run()` ends as ABORTED, GAME_OVER is broadcast and the queue is drained with 409. Then it is joined (max 5 s). |
| `sse-writer-<id>` (one per game) | `Broadcaster` (single-thread executor) | no | Does **all** socket writes for one game's event streams, so a slow or dead client can never block the game thread. One thread also keeps every client's events in order. Non-daemon, so events already queued, especially the final GAME_OVER, are still written during shutdown. It is created on the first event. | Kept across a rematch (the streams stay open). The game thread calls `Broadcaster.close()` after the session's last GAME_OVER: it closes every stream, then `shutdown()` + `awaitTermination(5 s)`. |
| `sse-keepalive` | `LudoServer` (scheduled executor) | **yes** | Every 10 s it asks each game's writer to send a `: keep-alive` comment. That keeps idle connections open and reveals dead clients: their write fails, they are removed and the game thread is told. Housekeeping only: nothing is lost if the JVM exits without the next keep-alive. | `shutdownNow()` in `LudoServer.stop()`. |
| `shutdown-hook` | `ServerMain` (`Runtime.addShutdownHook`) | no (hook threads always run to the end) | On Ctrl+C or a normal exit it runs `LudoServer.stop()`: refuse new requests (503), interrupt and join every game thread, stop the HTTP server, stop the keep-alive timer. | Ends when `stop()` returns. |

**Automatic next game (Task 10): no new threads.** The rematch delay is spent by `game-<id>` polling its own queue (state `GameOver`, every request gets 409), exactly like the pacing delay; the next `Game` is built and run on that same thread; the event streams, `sse-writer-<id>` and the version counter carry on. The winner box and its close button run on the EDT and send nothing.

## Not server threads

- `fake-RED` and similar threads, and `HttpClient-*`, are threads of the **tests**' fake clients. They are daemon, so a failing test can never keep the build running.
- `Common-Cleaner` and `ForkJoinPool.commonPool-*` belong to the JDK.

## Which thread touches what

| Data | Owner | How other threads see it |
|---|---|---|
| `Game`, coordinator state, seats, ack barrier, idempotency map | `game-<id>` only | They don't. They send commands through the queue and get replies through a `CompletableFuture`. |
| Latest STATE (`StateView`), state name, joined count | written by `game-<id>` | `volatile` fields holding immutable values; any thread may read them (GET /state, GET /games). |
| Version | written by `game-<id>` | `AtomicLong`; any thread may read it. |
| Event-stream list | changed by HTTP workers (connect) and `sse-writer-<id>` (failed write) | `CopyOnWriteArrayList`: safe to read from any thread. |
| Sockets of the event streams | `sse-writer-<id>` once the stream is registered | The HTTP worker that opens a stream writes only its first `retry:` line, before registering it. For a game that is already over, it writes the final STATE and GAME_OVER itself, and the stream is never registered. |
| All games | `ConcurrentHashMap` in `SessionRegistry` | Any thread; ids come from an `AtomicLong`. |

# Client threads (ludo-client)

Checked in a running GUI client (Red, game 2 on a live server) with `Thread.getAllStackTraces()` and `Thread.isDaemon()`. The rule is the same as on the server, but the reasons point the other way: in the client only the **GUI** (or, headless, the `main` thread waiting for GAME_OVER) may keep the JVM alive. Every thread the client starts itself is a daemon. So when the window is closed, the JVM ends by itself, even if a thread is still blocked on the network. The window uses `DISPOSE_ON_CLOSE`, not `System.exit`: once the last window is disposed, AWT ends the Event Dispatch Thread, only daemons are left and the JVM exits (checked: closing a client window ends the process with exit code 0).

| Thread | Created by | Daemon? (runtime) | Why | How it stops |
|---|---|---|---|---|
| `main` | JVM | no | Parses the options. **GUI:** it hands the window to the EDT with `invokeLater`, then returns. **Headless:** it starts the session and waits on the GAME_OVER latch, which keeps the JVM alive until the session is over: a GAME_OVER that announces a next game does not release it, so a headless player stays for every next game. | GUI: returns at once. Headless: returns after a GAME_OVER without a next game (or when the stream has closed for good), then the JVM exits. |
| `AWT-EventQueue-0` (EDT) | JDK (Swing) | **no** | All Swing work: building the windows, and applying every STATE, request, PAUSED and GAME_OVER (`SwingGameView` uses `invokeLater`). It also runs the game window's animation: a `javax.swing.Timer` fires every 33 ms **on the EDT** and repaints. So animation needs no thread of its own and never touches Swing from outside the EDT. Non-daemon, so it is what keeps a GUI client running. | AWT ends it after the last window is disposed. The window stops its animation timer when it closes, because a running Swing timer would keep posting events and so keep the EDT alive. |
| `AWT-Shutdown` | JDK (AWT) | no | AWT's helper that keeps AWT alive while windows exist. It is not started by our code. | Ends together with the EDT. |
| `client-start` | `ClientMain.openGame` | yes | One-off: opens the event stream (waits up to 10 s for it) and sends JOIN, so the EDT is never blocked on the network. | Ends after the JOIN. It was already gone at the time of the dump. |
| `event-stream` | `EventStreamListener` | **yes** | Blocking read of the SSE stream (`GET .../events`). It turns each frame into a `ServerEvent` and puts it on the controller's queue, and reconnects with backoff when the stream drops. A daemon, because a blocking socket read must never keep the JVM alive after the window is closed. | `close()` when the window closes, or by itself after a GAME_OVER without a next game (after one that announces a next game it keeps reading the same stream). |
| `client-controller` | `ClientController` | **yes** | Takes events from its `LinkedBlockingQueue` one by one (one order, no locks): checks the hash, waits for the view to apply a STATE, then ACKs; sends ROLL; hands decisions to the worker. A daemon because the EDT (or headless `main`) decides how long the client lives. | `stop()` when the window closes (interrupt), or by itself after a GAME_OVER without a next game. A NEW_GAME only resets the view. |
| `decision-worker` | `ClientController` (single-thread executor) | **yes** | Runs the colour's strategy (`SnapshotStrategyDecider`) on the request's snapshot and sends the DECISION, so the controller keeps taking events in the meantime. A short CPU task only. | `shutdownNow()` in `stop()`. |
| `HttpClient-N-Worker-*`, `HttpClient-N-SelectorManager` | JDK `java.net.http.HttpClient` | yes | Run the async requests (JOIN, ROLL, DECISION, ACK and their retries) and the stream's I/O. | Belong to the JDK; end with the JVM. |
| `AWT-Windows`, `TimerQueue`, `Java2D Disposer`, `DND Screen Updater`, `ForkJoinPool.commonPool-*`, `Common-Cleaner` | JDK | yes | JDK internals (native window events, `CompletableFuture` callbacks). `TimerQueue` only *schedules* Swing timers (the animation timer and tooltips); their actions run on the EDT. | End with the JVM. |

**Which thread touches what (client)**

| Data | Owner | How other threads see it |
|---|---|---|
| Swing components | EDT only | Other threads call `SwingGameView`, which uses `invokeLater`. `showState` returns a `CompletableFuture` that the EDT completes **after** it has applied the state, and only then does the controller ACK. |
| Event order | `client-controller` | `event-stream` only puts events on the `LinkedBlockingQueue`. |
| Last-Event-ID, backoff | `event-stream` only | Not shared. |
