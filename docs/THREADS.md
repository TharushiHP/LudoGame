# Threads of the coordinator server

Every thread the server runs, whether it is a daemon, and why. A JVM exits when only daemon threads are left. So a **non-daemon** thread is used for work that must not be cut off halfway. A **daemon** thread is used for housekeeping that may simply stop when the JVM exits.

The flags below were checked in a running server with `Thread.getAllStackTraces()`. `GameSessionTest` also asserts that `game-<id>` is non-daemon.

| Thread | Created by | Daemon? | Why | How it stops |
|---|---|---|---|---|
| `main` | JVM | no | Parses the options, starts `LudoServer`, registers the shutdown hook, then returns. | Returns straight after start-up. The JVM stays alive because of the non-daemon threads below. |
| `HTTP-Dispatcher` | JDK `HttpServer.start()` | no (inherited from `main`) | Accepts connections and hands each request to the worker pool. While it runs, the server is up. | `HttpServer.stop(1)` in `LudoHttpServer.stop()`. |
| `idle-timeout-task` | JDK `HttpServer` | yes | The JDK's timer that closes idle keep-alive connections. Housekeeping only. | Ends with the JVM or `HttpServer.stop`. |
| `http-worker-N` (fixed pool, 16) | `LudoHttpServer` (`NamedThreadFactory`) | no | Runs the handlers: parses JSON, puts a command on the game's queue and waits up to 5 s for the reply. Non-daemon, so a request in progress is answered rather than cut off. A **fixed** pool caps how many requests run at once; the bounded game queues then push back with 503. | `workers.shutdown()` + `awaitTermination(5 s)` after the HTTP server has stopped. |
| `game-<id>` (one per game) | `GameSession` | **no** | **The only thread that ever touches that game's `Game`** (thread confinement). It consumes the command queue, runs `Game.run()`, the ack barrier and pacing. Non-daemon, because the JVM must not exit in the middle of a game and leave clients waiting for a GAME_OVER that never comes. | Ends by itself after GAME_OVER. On shutdown, `GameSession.shutdown` interrupts it: `Game.run()` ends as ABORTED, GAME_OVER is broadcast and the queue is drained with 409. Then it is joined (max 5 s). |
| `sse-writer-<id>` (one per game) | `Broadcaster` (single-thread executor) | no | Does **all** socket writes for one game's event streams, so a slow or dead client can never block the game thread. One thread also keeps every client's events in order. Non-daemon, so events already queued, especially the final GAME_OVER, are still written during shutdown. It is created on the first event. | The game thread calls `Broadcaster.close()` at game over: it closes every stream, then `shutdown()` + `awaitTermination(5 s)`. |
| `sse-keepalive` | `LudoServer` (scheduled executor) | **yes** | Every 10 s it asks each game's writer to send a `: keep-alive` comment. That keeps idle connections open and reveals dead clients: their write fails, they are removed and the game thread is told. Housekeeping only: nothing is lost if the JVM exits without the next keep-alive. | `shutdownNow()` in `LudoServer.stop()`. |
| `shutdown-hook` | `ServerMain` (`Runtime.addShutdownHook`) | no (hook threads always run to the end) | On Ctrl+C or a normal exit it runs `LudoServer.stop()`: refuse new requests (503), interrupt and join every game thread, stop the HTTP server, stop the keep-alive timer. | Ends when `stop()` returns. |

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
