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
- Planned modules: ludo-core, ludo-shared, ludo-server, ludo-client, ludo-testclients as Maven modules, plus database/ for SQL scripts.
- Clean Architecture dependency rule: ludo-core must never import HTTP, Swing or JDBC classes.

## Consistency (top priority)
All four players must see the same game at the same time. Mechanisms:
- One thread per game touches the board (thread confinement), giving one total order of events.
- Lockstep turn cycle: TURN_START -> client sends ROLL -> server rolls and sends legal moves -> client sends MOVE -> server validates, applies, broadcasts FULL snapshot -> all 4 clients ACK with version + state hash -> paced delay -> next turn.
- Every state change increments a version. Requests carry turnId + expectedVersion; stale ones get HTTP 409. Requests carry a requestId so retries are idempotent.
- Ack barrier with java.util.concurrent.Phaser; hashes compared every turn.
- Server push via Server-Sent Events; reconnect resyncs using Last-Event-ID.
- Coordinator states (State pattern): WaitingForPlayers, AwaitingRoll, AwaitingMove, AwaitingAcks, Pacing, Paused, GameOver.
- Pacing via ScheduledExecutorService, configurable delay. Not real-time, but a game should take minutes, not hours.

## Threads
Be explicit about daemon vs non-daemon threads and justify each. Shutdown hook drains queues and saves state.

## Working rules
- Design patterns and SOLID principles from Assignment 1 must be preserved. Any change, removal or corrected label must be recorded in docs/CHANGES_FROM_A1.md with a reason.
- Every public class gets a short Javadoc stating its responsibility and any pattern/principle it demonstrates.
- Keep changes small and focused on the task given. Do not start later tasks early.
- After every task: run the tests, then explain what changed and why in plain language. I must be able to explain all of the code in a live demo.
