package ludo.server.coordinator.state;

import ludo.shared.PlayerColor;
import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.DecisionKind;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.DecisionRequest;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.RollCommand;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;
import ludo.shared.snapshot.MysterySnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/** Each coordinator state accepts only its own requests and rejects the rest with 409 (State pattern). */
class CoordinatorStateTest {

    private static final long VERSION = 5;
    private static final String HASH = "abc";

    private final FakeContext ctx = new FakeContext();

    private static final JoinRequest JOIN = new JoinRequest(PlayerColor.RED, "red", true);
    private static final RollCommand ROLL = new RollCommand(PlayerColor.RED, 3, VERSION, "r");
    private static final DecisionReply DECISION =
            DecisionReply.choosePiece(PlayerColor.RED, 9, VERSION, "d", OptionalInt.of(2), OptionalInt.empty());
    private static final AckCommand ACK = new AckCommand(PlayerColor.RED, VERSION, HASH, "a");

    @Test
    void waitingForPlayersAcceptsOnlyJoinAndEachColourOnce() {
        WaitingForPlayers state = new WaitingForPlayers();
        assertEquals(200, state.onJoin(JOIN, ctx).status());
        assertEquals(409, state.onJoin(JOIN, ctx).status(), "colour already taken");
        assertRejected(state, ROLL, DECISION, ACK);
    }

    @Test
    void awaitingRollAcceptsOnlyTheRightRoll() {
        AwaitingRoll state = new AwaitingRoll(PlayerColor.RED, 3, VERSION);
        assertEquals(409, state.onRoll(new RollCommand(PlayerColor.BLUE, 3, VERSION, "x"), ctx).status(), "wrong colour");
        assertEquals(409, state.onRoll(new RollCommand(PlayerColor.RED, 3, VERSION - 1, "x"), ctx).status(), "stale version");
        assertEquals(409, state.onRoll(new RollCommand(PlayerColor.RED, 2, VERSION, "x"), ctx).status(), "old turnId");
        assertFalse(state.isDone());
        assertEquals(200, state.onRoll(ROLL, ctx).status());
        assertTrue(state.isDone());
        assertEquals(409, state.onJoin(JOIN, ctx).status());
        assertEquals(409, state.onDecision(DECISION, ctx).status());
    }

    @Test
    void awaitingDecisionChecksVersionIdAndCandidates() {
        AwaitingDecision state = new AwaitingDecision(question(DecisionKind.CHOOSE_PIECE));
        assertEquals(409, state.onDecision(reply(PlayerColor.RED, 9, VERSION - 1, 2), ctx).status(), "stale version");
        assertEquals(409, state.onDecision(reply(PlayerColor.RED, 8, VERSION, 2), ctx).status(), "old decisionId");
        assertEquals(409, state.onDecision(reply(PlayerColor.GREEN, 9, VERSION, 2), ctx).status(), "wrong colour");
        assertEquals(409, state.onDecision(reply(PlayerColor.RED, 9, VERSION, 4), ctx).status(), "not a candidate");
        assertFalse(state.hasAnswer());
        assertEquals(200, state.onDecision(DECISION, ctx).status());
        assertEquals(OptionalInt.of(2), state.answer().piece());
        assertEquals(409, state.onRoll(ROLL, ctx).status());
    }

    @Test
    void moveFromBaseDecisionNeedsFromBase() {
        AwaitingDecision state = new AwaitingDecision(question(DecisionKind.MOVE_FROM_BASE));
        assertEquals(400, state.onDecision(reply(PlayerColor.RED, 9, VERSION, 1), ctx).status());
        assertEquals(200, state.onDecision(DecisionReply.moveFromBase(PlayerColor.RED, 9, VERSION, "d", true), ctx).status());
    }

    @Test
    void awaitingAcksCountsMatchingAcksAndResendsOnMismatch() {
        AckBarrier barrier = new AckBarrier(VERSION, HASH, EnumSet.of(PlayerColor.RED, PlayerColor.BLUE));
        AwaitingAcks state = new AwaitingAcks(barrier);

        assertEquals(409, state.onAck(new AckCommand(PlayerColor.RED, VERSION - 1, HASH, "x"), ctx).status(), "stale");
        assertEquals(409, state.onAck(new AckCommand(PlayerColor.RED, VERSION, "wrong", "x"), ctx).status(), "mismatch");
        assertEquals(List.of(PlayerColor.RED), ctx.resent);
        assertEquals(200, state.onAck(ACK, ctx).status());
        assertFalse(barrier.isComplete());
        assertEquals(200, state.onAck(new AckCommand(PlayerColor.BLUE, VERSION, HASH, "b"), ctx).status());
        assertTrue(barrier.isComplete());
        assertRejected(state, JOIN, ROLL, DECISION);
    }

    @Test
    void aClientThatKeepsSendingWrongHashesIsLeftOutOfTheBarrier() {
        AckBarrier barrier = new AckBarrier(VERSION, HASH, EnumSet.of(PlayerColor.RED));
        AwaitingAcks state = new AwaitingAcks(barrier);
        for (int i = 0; i < AckBarrier.MAX_RESENDS + 1; i++)
            state.onAck(new AckCommand(PlayerColor.RED, VERSION, "wrong", "x" + i), ctx);
        assertEquals(AckBarrier.MAX_RESENDS, ctx.resent.size());
        assertTrue(barrier.isComplete());
    }

    @Test
    void barrierDropsDisconnectedClients() {
        AckBarrier barrier = new AckBarrier(VERSION, HASH, EnumSet.of(PlayerColor.RED, PlayerColor.BLUE));
        assertEquals(EnumSet.of(PlayerColor.BLUE), barrier.dropDisconnected(colour -> colour == PlayerColor.RED));
        assertEquals(EnumSet.of(PlayerColor.RED), barrier.pending());
    }

    @Test
    void pacingAcceptsOnlyALateAckOfTheCurrentState() {
        Pacing state = new Pacing(500);
        assertEquals(200, state.onAck(ACK, ctx).status());
        assertEquals(409, state.onAck(new AckCommand(PlayerColor.RED, VERSION - 1, HASH, "x"), ctx).status());
        assertRejected(state, JOIN, ROLL, DECISION);
    }

    @Test
    void pausedPassesRequestsToTheStateThatWasWaiting() {
        AwaitingRoll waiting = new AwaitingRoll(PlayerColor.RED, 3, VERSION);
        Paused state = new Paused(PlayerColor.RED, waiting);
        assertTrue(state.name().startsWith("Paused(RED"));
        assertEquals(409, state.onRoll(new RollCommand(PlayerColor.GREEN, 3, VERSION, "x"), ctx).status());
        assertEquals(200, state.onRoll(ROLL, ctx).status());
        assertTrue(waiting.isDone(), "the missing ROLL still counts while paused");
    }

    @Test
    void gameOverRejectsEverything() {
        GameOver state = new GameOver(GameStatus.FINISHED);
        assertRejected(state, JOIN, ROLL, DECISION, ACK);
    }

    // --- helpers ---

    private static void assertRejected(CoordinatorState state, Object... requests) {
        FakeContext ctx = new FakeContext();
        for (Object request : requests) {
            Reply reply;
            if (request instanceof JoinRequest join)
                reply = state.onJoin(join, ctx);
            else if (request instanceof RollCommand roll)
                reply = state.onRoll(roll, ctx);
            else if (request instanceof DecisionReply decision)
                reply = state.onDecision(decision, ctx);
            else
                reply = state.onAck((AckCommand) request, ctx);
            assertEquals(409, reply.status(), state.name() + " must reject " + request);
            assertTrue(String.valueOf(reply.body().get("error")).contains("not accepted while " + state.name()));
        }
    }

    private static DecisionRequest question(DecisionKind kind) {
        GameSnapshot snapshot = new GameSnapshot(1, 1, PlayerColor.RED, 6, new MysterySnapshot(-1, 0),
                Map.of(), GameStatus.IN_PROGRESS, List.of(), Map.of());
        return new DecisionRequest(PlayerColor.RED, 9, kind, 6, kind == DecisionKind.CHOOSE_PIECE ? List.of(1, 2, 3) : List.of(),
                VERSION, snapshot);
    }

    private static DecisionReply reply(PlayerColor colour, long decisionId, long version, int piece) {
        return DecisionReply.choosePiece(colour, decisionId, version, "x", OptionalInt.of(piece), OptionalInt.empty());
    }

    /** Records what the states ask of the coordinator. */
    private static final class FakeContext implements CoordinatorContext {
        final EnumSet<PlayerColor> seats = EnumSet.noneOf(PlayerColor.class);
        final List<PlayerColor> resent = new ArrayList<>();

        @Override
        public boolean isSeatTaken(PlayerColor colour) {
            return seats.contains(colour);
        }

        @Override
        public int takeSeat(JoinRequest join) {
            seats.add(join.colour());
            return seats.size();
        }

        @Override
        public long currentVersion() {
            return VERSION;
        }

        @Override
        public String currentHash() {
            return HASH;
        }

        @Override
        public void resendStateTo(PlayerColor colour) {
            resent.add(colour);
        }
    }
}
