# LUDO-T Distributed — project rules

## What this project is
University assignment (COMP63038 Clean Coding & Concurrent Programming, Assignment 2). Starting point is my Assignment 1 code: a console simulation of LUDO-T (Ludo with extra rules T-1 to T-15) with four automated players: Red (aggressive capturer), Green (blocker), Yellow (speedrunner), Blue (cyclic, mystery-cell focused). Assignment 2 turns it into a multi-user, multi-tier, client-server application.

## Hard constraints
- Java 17, Maven multi-module (Maven Wrapper). VANILLA ONLY: no Spring, no Kafka, no frameworks. Allowed: JDK classes (com.sun.net.httpserver, java.net.http, java.util.concurrent, Swing, JDBC), JUnit 5, one JDBC driver.
- Mockito is allowed in tests only.
- Still a SIMULATION: all 4 players are automated. No human gameplay input. GUI controls are limited to connect/start/pause/speed/save/load.
- The GUI must be easy to understand and update live.
- Server-side concurrency must be provable with automated tests and Postman (so the server speaks HTTP).

## Architecture
- Central COORDINATOR SERVER owns the only authoritative board: dice, rules, move validation, mystery cell, turn order.
- Four THICK CLIENTS (Red, Green, Yellow, Blue), each on its own machine. Each runs its player behaviour (Strategy pattern) to choose moves, and a Swing GUI. Clients NEVER apply game rules to their own state; they only render snapshots from the server.
- Optional DATABASE tier (save-state mode) with an in-memory alternative, both behind a GameRepository port.
- Clean Architecture dependency rule: ludo-core must never import HTTP, Swing or JDBC classes. Enforced by DependencyRuleTest (ludo-core and ludo-shared) and PackageCycleTest (no package cycles in ludo-core).

## Modules (Maven multi-module; parent pom.xml, packaging pom)
| Module | Contents | Depends on |
|---|---|---|
| ludo-shared | ludo.shared: PlayerColor, Direction, BoardConstants, PieceLocation, EffectKind, PathMath; ludo.shared.snapshot (GameSnapshot and records); ludo.shared.decision (MoveDecider port, PieceChoice); ludo.shared.json (hand-written JsonWriter/JsonParser/JsonObjects); ludo.shared.protocol (SnapshotCodec, StateHasher, event and request records) | nothing |
| ludo-players | ludo.players: the four MoveStrategy behaviours, StrategyFactory, SnapshotStrategyDecider. Decide from snapshots only | ludo-shared |
| ludo-core | ludo.board, dice, effect, game, piece, player: the rules and the one authoritative board | ludo-shared (ludo-players at test scope only) |
| ludo-server | ludo.server: ServerMain (runnable jar), LudoServer, ConsoleSimulation; ludo.server.config (ServerConfig, ServerLog, NamedThreadFactory); ludo.server.coordinator (GameSession, Coordinator, CommandLoop, Broadcaster, RemoteTurnGate, RemoteMoveDecider, ...); ludo.server.coordinator.state (the 7 coordinator states, AckBarrier, Reply); ludo.server.http (LudoHttpServer, GamesHandler, SseSink); ludo.output.GameLogger; GoldenMasterTest + golden files | ludo-core, ludo-players |
| ludo-client | ludo.client: ClientMain (runnable jar), ClientOptions, ClientSession; ludo.client.net (ServerGateway, HttpServerGateway Remote Proxy, RetryPolicy, SseFrameParser, EventStreamListener); ludo.client.control (ClientController, GameView port, Identity); ludo.client.gui (Swing: ConnectWindow, GameWindow, TablePanel, BoardPainter (Figure 1), TokenPainter, Animator, LegendOverlay, WinnerOverlay, ...); ludo.client.gui.model (pure, tested: TableLayout, Route, PieceChange, Banner, DiceFaces, TokenText, Ending); ludo.client.console (ConsoleGameView, headless) | ludo-shared, ludo-players |
| ludo-testclients | placeholder (ludo.testclients.TestClientsApp) | nothing yet |
| database/ | SQL scripts (not created yet) | |

GameBuilder (core) has no default MoveDecider; the caller must pass one (ConsoleSimulation and core tests pass SnapshotStrategyDecider).
End condition (Rule 11 "may continue"): ludo.game.EndCondition ALL_PLACES (GameBuilder default; ConsoleSimulation keeps it, so the golden master is unchanged) or FIRST_WINNER (the game stops when the first player has all four tokens Home; only the winner is placed). The server defaults to FIRST_WINNER (--end-condition, or "endCondition" in POST /games). ServerGameTest's golden-master comparison game uses ALL_PLACES.

## Commands (Windows: use mvnw.cmd)
- Build and test everything: `./mvnw clean package` (or `./mvnw test`)
- Test one module: `./mvnw test -pl ludo-core -am`
- Golden master only: `./mvnw test -pl ludo-server -am -Dtest=GoldenMasterTest -Dsurefire.failIfNoSpecifiedTests=false`
- Run the coordinator server: `java -jar ludo-server/target/ludo-server.jar --port=8080 --turn-delay=500 --move-timeout=10000 --end-condition=FIRST_WINNER` (after `package`; all options optional, values in ms; --end-condition is FIRST_WINNER or ALL_PLACES, default FIRST_WINNER)
- Run the console simulation: `java -cp ludo-server/target/ludo-server.jar ludo.server.ConsoleSimulation --seed=7`
- Server tests only: `./mvnw test -pl ludo-server -am`
- Client tests only: `./mvnw test -pl ludo-client -am`
- Run a client: `java -jar ludo-client/target/ludo-client.jar` (connect window), or `--server=URL --game=ID --colour=RED|GREEN|YELLOW|BLUE|SPECTATOR [--name=TEXT] [--headless]` to skip it
- One-PC demo (server console + one spectator game window + 4 headless player clients in minimised consoles, game 1, seed 7): `scripts\start-demo.bat`; server output also in logs\server.log
- LAN set-up, client options and troubleshooting: docs/RUNNING.md
- Golden files live in ludo-server/src/test/resources/golden. Never regenerate them unless a behaviour change is intended and approved.

## Consistency (top priority)
All four players must see the same game at the same time. Mechanisms:
- One thread per game (`game-<id>`) touches the board (thread confinement), giving one total order of events. HTTP threads only queue commands (bounded ArrayBlockingQueue, 503 when full) and wait for the game thread's reply.
- Lockstep cycle, per roll (bonus rolls included): ROLL_REQUEST -> client sends ROLL -> server rolls -> DECISION_REQUEST (with the post-roll snapshot) for each decision -> client sends DECISION -> server validates, applies, broadcasts FULL STATE (version, snapshot, hash, log lines) -> all connected clients ACK with version + state hash -> pacing delay -> next roll.
- The version goes up by 1 on every STATE broadcast (every state change). ROLL carries turnId + expectedVersion, DECISION carries decisionId + expectedVersion; stale ones get HTTP 409. ROLL/DECISION/ACK carry a requestId: a repeated accepted requestId gets the stored reply and is not applied again.
- Ack barrier: a thread-confined EnumSet (AckBarrier) owned by the game thread, since ACKs arrive through the queue. (Not a Phaser: only one thread ever touches it.) Hashes (SHA-256 of canonical snapshot JSON, StateHasher) compared every roll; a mismatch re-sends STATE.
- Server push via Server-Sent Events, ids = event sequence; on (re)connect the client gets the current STATE and any open request at once (Last-Event-ID is logged).
- Coordinator states (State pattern): WaitingForPlayers, AwaitingRoll, AwaitingDecision, AwaitingAcks, Pacing, Paused, GameOver.
- Pacing: the game thread polls its queue until the --turn-delay deadline (never Thread.sleep), so it keeps answering requests. Not real-time, but a game should take minutes, not hours.
- No ROLL/DECISION within --move-timeout: Paused + PAUSED event; resumes when the answer arrives or the client reconnects; after 30 s more the server plays that colour with SnapshotStrategyDecider for the rest of the game (permanent substitution).

## Threads
Be explicit about daemon vs non-daemon threads and justify each: see docs/THREADS.md (keep it up to date). Shutdown hook (`shutdown-hook`) refuses new requests, interrupts game threads (games end ABORTED, GAME_OVER is sent, queues are drained with 409), closes event streams, then stops the HTTP server. Saving state is added with the database task.
Client: every thread the client starts (`event-stream`, `client-controller`, `decision-worker`, `client-start`) is a daemon; only the EDT (GUI) or the headless `main` waiting for GAME_OVER keeps the JVM alive. The window uses DISPOSE_ON_CLOSE, not System.exit. A client ACKs a STATE only after the view has applied it, always with its own hash.
GUI: one game window drawn like Figure 1 of the brief (docs/figure1.png). Animations only in the spectator window (it never ACKs); a player window draws every state at once. Technical info lives in the server console and the F2 developer view. Player boxes show the colour name only (no strategy words). A round "i" button opens a symbol legend drawn with the board's own painters (click or Esc closes it). A FIRST_WINNER ending shows "RED WINS!" in the winner's colour; other endings show the podium.

## Working rules
- Design patterns and SOLID principles from Assignment 1 must be preserved. Any change, removal or corrected label must be recorded in docs/CHANGES_FROM_A1.md with a reason.
- Every public class gets a short Javadoc stating its responsibility and any pattern/principle it demonstrates.
- Keep changes small and focused on the task given. Do not start later tasks early.
- After every task: run the tests, then explain what changed and why in plain language. I must be able to explain all of the code in a live demo.
