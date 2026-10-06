package ludo.client.control;

import ludo.client.net.ConnectionState;
import ludo.client.net.GameSummary;
import ludo.client.net.GatewayReply;
import ludo.client.net.ServerGateway;
import ludo.players.SnapshotStrategyDecider;
import ludo.shared.Direction;
import ludo.shared.EffectKind;
import ludo.shared.PieceLocation;
import ludo.shared.PlayerColor;
import ludo.shared.decision.PieceChoice;
import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.ClientRequest;
import ludo.shared.protocol.DecisionKind;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.DecisionRequest;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.NewGameEvent;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.ResumedEvent;
import ludo.shared.protocol.RollCommand;
import ludo.shared.protocol.RollRequest;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;
import ludo.shared.protocol.StateHasher;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;
import ludo.shared.snapshot.MysterySnapshot;
import ludo.shared.snapshot.PieceSnapshot;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The controller's protocol rules, with a fake server and a fake screen: no network, no Swing.
 * The controller runs on its real {@code client-controller} thread.
 */
class ClientControllerTest {

    private static final String GAME = "1";

    private final FakeGateway gateway = new FakeGateway();
    private final FakeView view = new FakeView();
    private ClientController controller;

    @AfterEach
    void stop() {
        if (controller != null)
            controller.stop();
    }

    private ClientController start(Identity me) {
        controller = new ClientController(gateway, GAME, me, view, new SnapshotStrategyDecider());
        controller.start();
        return controller;
    }

    // --- STATE and ACK ---

    @Test
    void stateIsAckedOnceWithTheRightHashAndOnlyAfterTheViewAppliedIt() throws Exception {
        start(Identity.player(PlayerColor.RED, "A"));
        StateEvent state = state(5, snapshot(GameStatus.IN_PROGRESS, Map.of()));

        controller.onEvent(state);
        CompletableFuture<Void> applied = view.nextShowState();
        Thread.sleep(200);
        assertTrue(gateway.sent(AckCommand.class).isEmpty(), "ACK sent before the screen showed the state");

        applied.complete(null);
        AckCommand ack = gateway.await(AckCommand.class);
        assertEquals(PlayerColor.RED, ack.colour());
        assertEquals(5, ack.version());
        assertEquals(StateHasher.hash(state.snapshot()), ack.hash());
        assertEquals(state.hash(), ack.hash());
        Thread.sleep(100);
        assertEquals(1, gateway.sent(AckCommand.class).size(), "exactly one ACK");
        assertEquals(List.of(true), view.syncedFlags);
    }

    @Test
    void aHashMismatchIsShownAndTheAckCarriesTheLocalHashNotTheServers() throws Exception {
        start(Identity.player(PlayerColor.GREEN, "B"));
        GameSnapshot snapshot = snapshot(GameStatus.IN_PROGRESS, Map.of());
        StateEvent tampered = new StateEvent(9, snapshot, "0000-not-the-hash", List.of());

        controller.onEvent(tampered);
        view.nextShowState().complete(null);
        AckCommand ack = gateway.await(AckCommand.class);

        assertEquals(List.of(false), view.syncedFlags, "the view is told the state is out of sync");
        assertNotEquals(tampered.hash(), ack.hash(), "never echo the server's hash");
        assertEquals(StateHasher.hash(snapshot), ack.hash(), "the server sees the mismatch and re-sends");
        assertTrue(view.warnings.stream().anyMatch(w -> w.contains("hash")));
    }

    @Test
    void theFinalStateIsNotAcked() throws Exception {
        start(Identity.player(PlayerColor.RED, "A"));
        controller.onEvent(state(40, snapshot(GameStatus.FINISHED, Map.of())));
        view.nextShowState().complete(null);
        controller.onEvent(new GameOverEvent(GameStatus.FINISHED, Map.of(PlayerColor.RED, 1)));
        assertTrue(controller.awaitGameOver(2_000));
        assertTrue(gateway.sent(AckCommand.class).isEmpty());
    }

    @Test
    void aSpectatorNeverJoinsNorAcks() throws Exception {
        start(Identity.spectator("watcher"));
        controller.join().get(1, TimeUnit.SECONDS);
        controller.onEvent(state(2, snapshot(GameStatus.IN_PROGRESS, Map.of())));
        view.nextShowState().complete(null);
        controller.onEvent(new RollRequest(PlayerColor.RED, 1, 2));
        controller.onEvent(new GameOverEvent(GameStatus.ABORTED, Map.of()));
        assertTrue(controller.awaitGameOver(2_000));
        assertTrue(gateway.requests.isEmpty(), "sent: " + gateway.requests);
    }

    // --- requests ---

    @Test
    void myRollRequestIsAnsweredWithTurnIdAndVersion() throws Exception {
        start(Identity.player(PlayerColor.YELLOW, "C"));
        controller.onEvent(new RollRequest(PlayerColor.YELLOW, 17, 33));
        RollCommand roll = gateway.await(RollCommand.class);
        assertEquals(PlayerColor.YELLOW, roll.colour());
        assertEquals(17, roll.turnId());
        assertEquals(33, roll.expectedVersion());
        assertFalse(roll.requestId().isBlank());
    }

    @Test
    void requestsForOtherColoursAreOnlyShown() throws Exception {
        start(Identity.player(PlayerColor.RED, "A"));
        GameSnapshot s = snapshot(GameStatus.IN_PROGRESS, Map.of());
        controller.onEvent(new RollRequest(PlayerColor.BLUE, 1, 3));
        controller.onEvent(new DecisionRequest(PlayerColor.BLUE, 4, DecisionKind.CHOOSE_PIECE, 3, List.of(1, 2), 3, s));
        controller.onEvent(new DecisionRequest(PlayerColor.GREEN, 5, DecisionKind.MOVE_FROM_BASE, 6, List.of(), 3, s));
        controller.onEvent(new GameOverEvent(GameStatus.ABORTED, Map.of()));
        assertTrue(controller.awaitGameOver(2_000));
        Thread.sleep(100); // a decision would run on the worker thread
        assertTrue(gateway.requests.isEmpty(), "sent: " + gateway.requests);
        assertEquals(3, view.requests.size(), "but all three are shown");
    }

    @Test
    void bluesMemoIsPassedThroughToTheServer() throws Exception {
        start(Identity.player(PlayerColor.BLUE, "D"));
        GameSnapshot s = snapshot(GameStatus.IN_PROGRESS, Map.of(PlayerColor.BLUE, 1));
        DecisionRequest question = new DecisionRequest(PlayerColor.BLUE, 8, DecisionKind.CHOOSE_PIECE, 2,
                List.of(1, 2, 3, 4), 21, s);
        PieceChoice expected = new SnapshotStrategyDecider().choosePiece(s, PlayerColor.BLUE, 2, List.of(1, 2, 3, 4));
        assertTrue(expected.memo().isPresent(), "test setup: Blue must return a memo here");

        controller.onEvent(question);
        DecisionReply reply = gateway.await(DecisionReply.class);

        assertEquals(8, reply.decisionId());
        assertEquals(21, reply.expectedVersion());
        assertEquals(expected.piece(), reply.piece());
        assertEquals(expected.memo(), reply.memo());
    }

    @Test
    void moveFromBaseIsAnsweredWithTheStrategysPreference() throws Exception {
        start(Identity.player(PlayerColor.RED, "A"));
        GameSnapshot s = snapshot(GameStatus.IN_PROGRESS, Map.of());
        controller.onEvent(new DecisionRequest(PlayerColor.RED, 3, DecisionKind.MOVE_FROM_BASE, 6, List.of(), 12, s));
        DecisionReply reply = gateway.await(DecisionReply.class);
        assertEquals(new SnapshotStrategyDecider().prefersMoveFromBase(s, PlayerColor.RED), reply.fromBase().orElseThrow());
        assertTrue(reply.piece().isEmpty());
    }

    // --- join and other events ---

    @Test
    void joinDeclaresTheStrategysRule7Behaviour() throws Exception {
        for (PlayerColor colour : PlayerColor.values()) {
            ClientController c = new ClientController(gateway, GAME, Identity.player(colour, null), view, new SnapshotStrategyDecider());
            c.join().get(1, TimeUnit.SECONDS);
        }
        List<JoinRequest> joins = gateway.sent(JoinRequest.class);
        assertEquals(4, joins.size());
        for (JoinRequest join : joins)
            assertEquals(join.colour() != PlayerColor.BLUE, join.triesOtherPiecesWhenBlocked(), join.colour().name());
        assertEquals("Player Red", joins.get(0).clientName());
    }

    @Test
    void aRefusedJoinIsShownAndTheClientCarriesOn() throws Exception {
        gateway.nextStatus = 409;
        start(Identity.player(PlayerColor.RED, "A"));
        controller.join().get(1, TimeUnit.SECONDS);
        assertTrue(view.warnings.stream().anyMatch(w -> w.contains("already joined")), view.warnings.toString());
    }

    @Test
    void pausedResumedAndGameOverReachTheView() throws Exception {
        start(Identity.player(PlayerColor.BLUE, "D"));
        controller.onEvent(new PausedEvent(PlayerColor.BLUE, "no ROLL"));
        controller.onEvent(new ResumedEvent(PlayerColor.BLUE, true));
        controller.onEvent(new GameOverEvent(GameStatus.FINISHED, Map.of(PlayerColor.BLUE, 2)));
        assertTrue(controller.awaitGameOver(2_000));
        assertEquals(List.of("PAUSED", "RESUMED", "GAME_OVER"), view.lifecycle);
        assertTrue(view.warnings.stream().anyMatch(w -> w.contains("server now plays Blue")));
    }

    @Test
    void aGameOverWithANextGameKeepsTheControllerRunningUntilTheLastOne() throws Exception {
        start(Identity.player(PlayerColor.RED, "A"));
        controller.onEvent(new GameOverEvent(GameStatus.FINISHED, Map.of(PlayerColor.RED, 1), 10_000));
        assertFalse(controller.awaitGameOver(300), "a next game follows: the player stays");

        controller.onEvent(new NewGameEvent(2, 99));
        controller.onEvent(new RollRequest(PlayerColor.RED, 50, 120));
        RollCommand roll = gateway.await(RollCommand.class);
        assertEquals(50, roll.turnId(), "still answering after the first GAME_OVER");

        controller.onEvent(new GameOverEvent(GameStatus.FINISHED, Map.of(PlayerColor.GREEN, 1)));
        assertTrue(controller.awaitGameOver(2_000), "the last GAME_OVER ends the session");
        assertEquals(List.of("GAME_OVER", "NEW_GAME 2", "GAME_OVER"), view.lifecycle);
    }

    @Test
    void aStreamClosedForGoodEndsTheSession() throws Exception {
        start(Identity.player(PlayerColor.RED, "A"));
        controller.onConnection(ConnectionState.CLOSED);
        assertTrue(controller.awaitGameOver(1_000));
    }

    // --- test data ---

    private static StateEvent state(long version, GameSnapshot snapshot) {
        return new StateEvent(version, snapshot, StateHasher.hash(snapshot), List.of("log line v" + version));
    }

    /** Red and Blue each have pieces 1 and 2 on the board; the rest are at base. */
    private static GameSnapshot snapshot(GameStatus status, Map<PlayerColor, Integer> memo) {
        List<PieceSnapshot> pieces = new ArrayList<>();
        for (PlayerColor colour : List.of(PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN))
            for (int n = 1; n <= 4; n++) {
                boolean onBoard = (colour == PlayerColor.BLUE || colour == PlayerColor.RED) && n <= 2;
                int cell = colour == PlayerColor.BLUE ? 20 + n * 5 : 3 + n * 4;
                pieces.add(new PieceSnapshot(colour, n, onBoard ? PieceLocation.MAIN_PATH : PieceLocation.BASE,
                        onBoard ? cell : -1, Direction.CLOCKWISE, 0, EffectKind.NONE, 0, false));
            }
        Map<PlayerColor, Integer> places = new EnumMap<>(PlayerColor.class);
        for (PlayerColor colour : PlayerColor.values())
            places.put(colour, 0);
        return new GameSnapshot(3, 10, PlayerColor.BLUE, 2, new MysterySnapshot(-1, 0), places, status, pieces, memo);
    }

    // --- fakes ---

    /** Records every request and answers at once with {@link #nextStatus}. */
    private static final class FakeGateway implements ServerGateway {

        final List<ClientRequest> requests = new CopyOnWriteArrayList<>();
        volatile int nextStatus = 200;

        <T extends ClientRequest> List<T> sent(Class<T> type) {
            return requests.stream().filter(type::isInstance).map(type::cast).toList();
        }

        <T extends ClientRequest> T await(Class<T> type) throws InterruptedException {
            long deadline = System.currentTimeMillis() + 2_000;
            while (System.currentTimeMillis() < deadline) {
                List<T> found = sent(type);
                if (!found.isEmpty())
                    return found.get(0);
                Thread.sleep(10);
            }
            throw new AssertionError("no " + type.getSimpleName() + " sent; sent: " + requests);
        }

        private CompletableFuture<GatewayReply> record(ClientRequest request) {
            requests.add(request);
            return CompletableFuture.completedFuture(new GatewayReply(nextStatus,
                    nextStatus == 200 ? Map.of() : Map.of("error", "refused by fake")));
        }

        @Override
        public CompletableFuture<List<GameSummary>> listGames() {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public CompletableFuture<GatewayReply> createGame(Long seed, Long turnDelayMs) {
            return CompletableFuture.completedFuture(new GatewayReply(201, Map.of("gameId", "1")));
        }

        @Override
        public CompletableFuture<GatewayReply> join(String gameId, JoinRequest join) {
            return record(join);
        }

        @Override
        public CompletableFuture<GatewayReply> roll(String gameId, RollCommand roll) {
            return record(roll);
        }

        @Override
        public CompletableFuture<GatewayReply> decision(String gameId, DecisionReply decision) {
            return record(decision);
        }

        @Override
        public CompletableFuture<GatewayReply> ack(String gameId, AckCommand ack) {
            return record(ack);
        }
    }

    /** Hands each showState future to the test, which decides when the "screen" has applied it. */
    private static final class FakeView implements GameView {

        final BlockingQueue<CompletableFuture<Void>> pending = new LinkedBlockingQueue<>();
        final List<Boolean> syncedFlags = new CopyOnWriteArrayList<>();
        final List<ServerEvent> requests = new CopyOnWriteArrayList<>();
        final List<String> lifecycle = new CopyOnWriteArrayList<>();
        final List<String> warnings = new CopyOnWriteArrayList<>();

        CompletableFuture<Void> nextShowState() throws InterruptedException {
            CompletableFuture<Void> future = pending.poll(2, TimeUnit.SECONDS);
            assertNotNull(future, "the controller never showed the state");
            return future;
        }

        @Override
        public CompletableFuture<Void> showState(StateEvent state, boolean synced) {
            syncedFlags.add(synced);
            CompletableFuture<Void> applied = new CompletableFuture<>();
            pending.add(applied);
            return applied;
        }

        @Override
        public void showRequest(ServerEvent request) {
            requests.add(request);
        }

        @Override
        public void showPaused(PausedEvent paused) {
            lifecycle.add("PAUSED");
        }

        @Override
        public void showResumed(ResumedEvent resumed) {
            lifecycle.add("RESUMED");
        }

        @Override
        public void showGameOver(GameOverEvent gameOver) {
            lifecycle.add("GAME_OVER");
        }

        @Override
        public void showNewGame(NewGameEvent newGame) {
            lifecycle.add("NEW_GAME " + newGame.gameNumber());
        }

        @Override
        public void showConnection(ConnectionState state) {
        }

        @Override
        public void showWarning(String message) {
            warnings.add(message);
        }
    }
}
