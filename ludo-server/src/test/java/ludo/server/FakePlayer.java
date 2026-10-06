package ludo.server;

import ludo.players.SnapshotStrategyDecider;
import ludo.shared.PlayerColor;
import ludo.shared.decision.MoveDecider;
import ludo.shared.decision.PieceChoice;
import ludo.shared.json.JsonParser;
import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.DecisionKind;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.DecisionRequest;
import ludo.shared.protocol.EventType;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.RollCommand;
import ludo.shared.protocol.RollRequest;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;
import ludo.shared.protocol.StateHasher;
import ludo.shared.snapshot.GameStatus;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * A scripted client for one colour, speaking the real protocol over HTTP: it reads the event
 * stream on its own thread and, depending on its mode, answers ROLL_REQUEST and DECISION_REQUEST
 * with the same snapshot strategies the console game uses, and ACKs every STATE with the hash it
 * computes itself from the snapshot it received. Everything it sees and every reply it gets is
 * recorded for the test to check.
 */
final class FakePlayer {

    enum Mode {
        /** Rolls, decides and ACKs: a complete automated client. */
        AUTO,
        /** Only ACKs; the test sends ROLL and DECISION itself. */
        ACK_ONLY,
        /** Connected but never answers anything. */
        SILENT
    }

    final PlayerColor colour;
    final BlockingQueue<ServerEvent> events = new LinkedBlockingQueue<>();
    final List<String> gameLog = Collections.synchronizedList(new ArrayList<>());
    final List<String> problems = Collections.synchronizedList(new ArrayList<>());
    final List<Integer> ackStatuses = Collections.synchronizedList(new ArrayList<>());
    final CountDownLatch gameOver = new CountDownLatch(1);
    volatile GameOverEvent result;

    private final ServerFixture fixture;
    private final String gameId;
    private final Mode mode;
    private final MoveDecider strategy = new SnapshotStrategyDecider();
    private volatile Stream<String> stream;

    FakePlayer(ServerFixture fixture, String gameId, PlayerColor colour, Mode mode) {
        this.fixture = fixture;
        this.gameId = gameId;
        this.colour = colour;
        this.mode = mode;
    }

    void openEvents() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(
                URI.create(fixture.base + "/games/" + gameId + "/events?colour=" + colour)).GET().build();
        HttpResponse<Stream<String>> response = fixture.http
                .sendAsync(request, HttpResponse.BodyHandlers.ofLines()).get(5, TimeUnit.SECONDS);
        if (response.statusCode() != 200)
            throw new IllegalStateException("events for " + colour + ": HTTP " + response.statusCode());
        stream = response.body();
        Thread reader = new Thread(() -> read(stream.iterator()), "fake-" + colour);
        reader.setDaemon(true); // test helper: must never keep the test JVM alive
        reader.start();
    }

    void join() throws Exception {
        JoinRequest join = new JoinRequest(colour, "fake-" + colour, strategy.triesOtherPiecesWhenBlocked(colour));
        HttpResponse<String> response = fixture.post("/games/" + gameId + "/join", join.toJson());
        if (response.statusCode() != 200)
            throw new IllegalStateException("join " + colour + ": " + response.statusCode() + " " + response.body());
    }

    void stop() {
        Stream<String> s = stream;
        if (s != null)
            s.close();
    }

    /** Waits for the next event of this type (others are skipped) that matches the filter. */
    <T extends ServerEvent> T next(Class<T> type, Predicate<T> filter) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (true) {
            ServerEvent event = events.poll(deadline - System.nanoTime(), TimeUnit.NANOSECONDS);
            if (event == null)
                throw new AssertionError(colour + " received no " + type.getSimpleName() + " in time");
            if (type.isInstance(event) && filter.test(type.cast(event)))
                return type.cast(event);
        }
    }

    // --- protocol actions, also used directly by tests ---

    HttpResponse<String> roll(long turnId, long expectedVersion, String requestId) throws Exception {
        return fixture.post("/games/" + gameId + "/roll", new RollCommand(colour, turnId, expectedVersion, requestId).toJson());
    }

    HttpResponse<String> decide(DecisionRequest question, long expectedVersion, String requestId) throws Exception {
        DecisionReply reply;
        if (question.kind() == DecisionKind.MOVE_FROM_BASE) {
            reply = DecisionReply.moveFromBase(colour, question.decisionId(), expectedVersion, requestId,
                    strategy.prefersMoveFromBase(question.snapshot(), colour));
        } else {
            PieceChoice choice = strategy.choosePiece(question.snapshot(), colour, question.roll(), question.candidates());
            reply = DecisionReply.choosePiece(colour, question.decisionId(), expectedVersion, requestId,
                    choice.piece(), choice.memo());
        }
        return fixture.post("/games/" + gameId + "/decision", reply.toJson());
    }

    HttpResponse<String> ack(long version, String hash) throws Exception {
        return fixture.post("/games/" + gameId + "/ack", new AckCommand(colour, version, hash, newId()).toJson());
    }

    static String newId() {
        return UUID.randomUUID().toString();
    }

    // --- event stream ---

    private void read(Iterator<String> lines) {
        String type = null;
        String data = null;
        try {
            while (lines.hasNext()) {
                String line = lines.next();
                if (line.startsWith("event: ")) {
                    type = line.substring(7);
                } else if (line.startsWith("data: ")) {
                    data = line.substring(6);
                } else if (line.isEmpty() && type != null && data != null) {
                    handle(ServerEvent.fromJson(EventType.valueOf(type), JsonParser.parseObject(data)));
                    type = null;
                    data = null;
                }
            }
        } catch (Exception e) {
            if (gameOver.getCount() > 0)
                problems.add(colour + " stream ended: " + e);
        }
    }

    private void handle(ServerEvent event) throws Exception {
        events.add(event);
        if (event instanceof StateEvent state) {
            onState(state);
        } else if (event instanceof RollRequest request && request.colour() == colour && mode == Mode.AUTO) {
            expectOk(roll(request.turnId(), request.version(), newId()), "ROLL");
        } else if (event instanceof DecisionRequest question && question.colour() == colour && mode == Mode.AUTO) {
            expectOk(decide(question, question.version(), newId()), "DECISION");
        } else if (event instanceof GameOverEvent over) {
            result = over;
            gameOver.countDown();
        }
    }

    private void onState(StateEvent state) throws Exception {
        String myHash = StateHasher.hash(state.snapshot());
        if (!myHash.equals(state.hash()))
            problems.add(colour + " computed a different hash for v" + state.version());
        gameLog.addAll(state.log());
        // A STATE of a finished game needs no ACK: there is no next turn to hold back.
        if (mode == Mode.SILENT || state.snapshot().status() != GameStatus.IN_PROGRESS)
            return;
        HttpResponse<String> response = ack(state.version(), myHash);
        ackStatuses.add(response.statusCode());
        if (response.statusCode() != 200)
            problems.add(colour + " ACK v" + state.version() + " -> " + response.statusCode() + " " + response.body());
    }

    private void expectOk(HttpResponse<String> response, String what) {
        if (response.statusCode() != 200)
            problems.add(colour + " " + what + " -> " + response.statusCode() + " " + response.body());
    }
}
