package ludo.server.coordinator;

import ludo.game.Game;
import ludo.game.GameBuilder;
import ludo.server.config.ServerConfig;
import ludo.server.config.ServerLog;
import ludo.server.coordinator.state.AckBarrier;
import ludo.server.coordinator.state.AwaitingAcks;
import ludo.server.coordinator.state.CoordinatorContext;
import ludo.server.coordinator.state.CoordinatorState;
import ludo.server.coordinator.state.GameOver;
import ludo.server.coordinator.state.Pacing;
import ludo.server.coordinator.state.Paused;
import ludo.server.coordinator.state.Reply;
import ludo.server.coordinator.state.WaitingForPlayers;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.EventType;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.NewGameEvent;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.ResumedEvent;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;
import ludo.shared.protocol.StateHasher;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

final class Coordinator implements CoordinatorContext {

    private final GameSession session;
    private final ServerConfig config;
    private final ServerLog log;
    private final Broadcaster broadcaster;
    private final PlayerSeats seats = new PlayerSeats();
    private final BufferingEventListener gameLog;
    private final CommandLoop loop;
    private final Set<PlayerColor> connectedSincePause = EnumSet.noneOf(PlayerColor.class);
    private Game game;
    private boolean firstStateSent;
    private long turnId;
    private long decisionId;
    private ServerEvent pendingRequest;
    /**
     * Between two games (rematch on): the GAME_OVER that announced the next game,
     * and when it starts.
     */
    private GameOverEvent betweenGames;
    private long nextGameAt;
    private final Random seeds = new Random();

    Coordinator(GameSession session, BlockingQueue<Command> queue, Broadcaster broadcaster) {
        this.session = session;
        this.config = session.config();
        this.log = session.log();
        this.broadcaster = broadcaster;
        this.gameLog = new BufferingEventListener(config.echoGameLog(), log);
        this.loop = new CommandLoop(queue, log, this, seats, this::onConnected, this::onLost,
                session::publishStateName);
    }

    void run() {
        GameStatus status = GameStatus.ABORTED;
        try {
            loop.enter(new WaitingForPlayers());
            loop.awaitForever(seats::isFull);
            log.log("all four colours joined; game " + session.id() + " starts with seed " + session.seed());
            while (true) {
                status = playOneGame();
                if (config.rematchDelayMs() <= 0)
                    break;
                announceGameOver(status);
                status = GameStatus.ABORTED; // a shutdown during the wait ends the session as ABORTED
                game = null; // ...without sending the old game's final state again
                loop.awaitUntil(() -> false, nextGameAt);
                startNextGame();
            }
        } catch (InterruptedException e) {
            log.log("stopped while waiting for players");
        } catch (RuntimeException e) {
            log.log("game failed: " + e);
            e.printStackTrace(config.out());
        } finally {
            finish(status);
        }
    }

    /** One game from the first roll to the end, with the current seed. */
    private GameStatus playOneGame() throws InterruptedException {
        game = new GameBuilder()
                .withSeed(session.seed())
                .withEndCondition(session.endCondition())
                .withMoveDecider(new RemoteMoveDecider(this))
                .withTurnGate(new RemoteTurnGate(this))
                .withListener(gameLog)
                .build();
        game.run();
        return game.snapshot().status();
    }

    /**
     * Rematch on: the final STATE (no ACK needed), then GAME_OVER announcing the
     * next game. The
     * event streams stay open; the coordinator waits in GameOver, which answers
     * every request with 409.
     */
    private void announceGameOver(GameStatus status) {
        GameSnapshot last = game.snapshot();
        publishState(last);
        nextGameAt = deadline(config.rematchDelayMs());
        betweenGames = new GameOverEvent(status, last.finishPositions(), config.rematchDelayMs());
        broadcaster.broadcast(betweenGames);
        loop.enter(new GameOver(status));
        log.log("game " + session.id() + " #" + session.gameNumber() + " over: " + status
                + "; the next game starts in " + config.rematchDelayMs() + " ms");
    }

    /**
     * A new seed and game number, NEW_GAME to everyone, and a clean slate for the
     * per-game fields.
     */
    private void startNextGame() {
        int number = session.gameNumber() + 1;
        long seed = seeds.nextLong();
        session.publishNextGame(number, seed);
        betweenGames = null;
        firstStateSent = false;
        connectedSincePause.clear();
        setPendingRequest(null);
        log.log("game " + session.id() + " #" + number + " starts with seed " + seed + " (same seats; "
                + "substituted: " + seats.substitutedColours() + ")");
        broadcaster.broadcast(new NewGameEvent(number, seed));
    }

    // --- operations used by RemoteTurnGate and RemoteMoveDecider ---

    /**
     * Before the first roll: one STATE with the introduction and roll-off, so every
     * client starts in step.
     */
    void ensureFirstState() throws InterruptedException {
        if (!firstStateSent) {
            firstStateSent = true;
            publishStateAndAwaitAcks(game.snapshot());
        }
    }

    /**
     * Lockstep step 1: version + 1, broadcast the full STATE with its hash and the
     * new log lines.
     * Step 2: wait until every connected client has ACKed this version with the
     * same hash.
     * Clients that disconnect leave the barrier (they get the STATE again when they
     * reconnect);
     * clients still missing after the move timeout are logged and the game goes on
     * without them.
     */
    void publishStateAndAwaitAcks(GameSnapshot snapshot) throws InterruptedException {
        StateView view = publishState(snapshot);
        Set<PlayerColor> expected = EnumSet.noneOf(PlayerColor.class);
        for (PlayerColor colour : seats.activeClients())
            if (broadcaster.isConnected(colour))
                expected.add(colour);
        AckBarrier barrier = new AckBarrier(view.version(), view.hash(), expected);
        loop.enter(new AwaitingAcks(barrier));
        BooleanSupplier allAcked = () -> {
            Set<PlayerColor> dropped = barrier.dropDisconnected(broadcaster::isConnected);
            if (!dropped.isEmpty())
                log.log("disconnected, removed from the ack barrier of v" + view.version() + ": " + dropped);
            return barrier.isComplete();
        };
        if (!loop.awaitUntil(allAcked, deadline(config.moveTimeoutMs())))
            log.log("no ACK for v" + view.version() + " from " + barrier.pending() + " within "
                    + config.moveTimeoutMs() + " ms; continuing without them");
    }

    /**
     * Step 3: the turn delay. Requests are still answered meanwhile; the thread
     * never sleeps.
     */
    void pace() throws InterruptedException {
        long delay = session.turnDelayMs();
        if (delay <= 0)
            return;
        loop.enter(new Pacing(delay));
        loop.awaitUntil(() -> false, deadline(delay));
    }

    boolean awaitPlayer(PlayerColor colour, CoordinatorState waiting, ServerEvent request,
            BooleanSupplier answered) throws InterruptedException {
        setPendingRequest(request);
        try {
            broadcaster.broadcast(request);
            loop.enter(waiting);
            while (true) {
                if (loop.awaitUntil(answered, deadline(config.moveTimeoutMs())))
                    return true;
                String expected = request.type() == EventType.ROLL_REQUEST ? "ROLL" : "DECISION";
                connectedSincePause.remove(colour);
                loop.enter(new Paused(colour, waiting));
                broadcaster.broadcast(new PausedEvent(colour, "no " + expected + " from " + colour
                        + " within " + config.moveTimeoutMs() + " ms"));
                boolean back = loop.awaitUntil(() -> answered.getAsBoolean() || connectedSincePause.contains(colour),
                        deadline(config.substituteAfterMs()));
                if (back) {
                    broadcaster.broadcast(new ResumedEvent(colour, false));
                    if (answered.getAsBoolean())
                        return true;
                    loop.enter(waiting); // reconnected: it has been sent STATE and the request again
                    continue;
                }
                seats.substitute(colour);
                log.log(colour + " sent no " + expected + " for " + config.substituteAfterMs()
                        + " ms while paused: the server plays " + colour + " (SnapshotStrategyDecider) from now on");
                broadcaster.broadcast(new ResumedEvent(colour, true));
                return false;
            }
        } finally {
            setPendingRequest(null);
        }
    }

    long nextTurnId() {
        return ++turnId;
    }

    long nextDecisionId() {
        return ++decisionId;
    }

    boolean isSubstituted(PlayerColor colour) {
        return seats.isSubstituted(colour);
    }

    boolean triesOtherPiecesWhenBlocked(PlayerColor colour) {
        return seats.triesOtherPiecesWhenBlocked(colour);
    }

    void log(String message) {
        log.log(message);
    }

    // --- CoordinatorContext (called by the states) ---

    @Override
    public boolean isSeatTaken(PlayerColor colour) {
        return seats.isTaken(colour);
    }

    @Override
    public int takeSeat(JoinRequest join) {
        int joined = seats.take(join);
        session.publishJoined(joined);
        session.publishTaken(seats.taken());
        log.log(join.colour() + " joined as \"" + join.clientName() + "\" (triesOtherPiecesWhenBlocked="
                + join.triesOtherPiecesWhenBlocked() + "), " + joined + "/4");
        return joined;
    }

    @Override
    public long currentVersion() {
        return session.version();
    }

    @Override
    public String currentHash() {
        StateView latest = session.latestState();
        return latest == null ? null : latest.hash();
    }

    @Override
    public void resendStateTo(PlayerColor colour) {
        StateView latest = session.latestState();
        log.log("hash mismatch from " + colour + " for v" + latest.version() + ": STATE re-sent to " + colour);
        broadcaster.sendTo(colour, new StateEvent(latest.version(), latest.snapshot(), latest.hash(), List.of()));
    }

    // --- event-stream connections (commands from the HTTP side) ---

    /**
     * A (re)connected client is brought up to date at once: the current STATE and
     * any open request.
     */
    private void onConnected(EventSink sink) {
        log.log("event stream " + sink.name() + " connected; " + broadcaster.size() + " open");
        if (sink.colour() != null)
            connectedSincePause.add(sink.colour());
        StateView latest = session.latestState();
        if (latest != null)
            broadcaster.sendTo(sink, new StateEvent(latest.version(), latest.snapshot(), latest.hash(), List.of()));
        if (pendingRequest != null)
            broadcaster.sendTo(sink, pendingRequest);
        if (betweenGames != null) {
            long left = Math.max(1, TimeUnit.NANOSECONDS.toMillis(nextGameAt - System.nanoTime()));
            broadcaster.sendTo(sink, new GameOverEvent(betweenGames.status(), betweenGames.finishPositions(), left));
        }
    }

    private void onLost(EventSink sink) {
        log.log("event stream " + sink.name() + " closed; " + broadcaster.size() + " open");
    }

    // --- helpers ---

    /**
     * The open ROLL_REQUEST or DECISION_REQUEST (or null), also published for GET
     * /state.
     */
    private void setPendingRequest(ServerEvent request) {
        pendingRequest = request;
        session.publishOpenRequest(request);
    }

    private StateView publishState(GameSnapshot snapshot) {
        long version = session.nextVersion();
        StateView view = new StateView(version, snapshot, StateHasher.hash(snapshot));
        session.publishLatest(view);
        broadcaster.broadcast(new StateEvent(version, snapshot, view.hash(), gameLog.drain()));
        return view;
    }

    private void finish(GameStatus status) {
        boolean interrupted = Thread.interrupted(); // clear it so the clean-up below can wait
        try {
            Map<PlayerColor, Integer> places = Map.of();
            if (game != null) {
                GameSnapshot last = game.snapshot();
                places = last.finishPositions();
                publishState(last); // the GAME OVER lines; a final state needs no ACK
            }
            GameOverEvent result = new GameOverEvent(status, places);
            broadcaster.broadcast(result);
            loop.enter(new GameOver(status));
            session.markClosed(result);
            loop.drain(Reply.conflict("game " + session.id() + " is over"));
            broadcaster.close();
            log.log("game " + session.id() + " over: " + status);
        } catch (InterruptedException e) {
            interrupted = true;
        } finally {
            if (interrupted)
                Thread.currentThread().interrupt();
        }
    }

    private static long deadline(long millis) {
        return System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(millis);
    }
}
