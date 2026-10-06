package ludo.players;

import ludo.shared.Direction;
import ludo.shared.decision.PieceChoice;
import ludo.shared.snapshot.GameSnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.OptionalInt;

import static ludo.shared.PlayerColor.BLUE;
import static ludo.shared.PlayerColor.GREEN;
import static ludo.shared.PlayerColor.RED;
import static ludo.shared.PlayerColor.YELLOW;
import static org.junit.jupiter.api.Assertions.*;

/**
 * The four behaviours, each decided from a {@link GameSnapshot} only. Same scenarios as the A1
 * StrategyTest, rebuilt as snapshots, plus the decider memo (Blue's cycle position).
 */
class StrategyTest {

    private static final List<Integer> ALL_PIECES = List.of(1, 2, 3, 4);

    private final StrategyFactory factory = new StrategyFactory();
    private final MoveStrategy red = factory.create(RED);
    private final MoveStrategy green = factory.create(GREEN);
    private final MoveStrategy yellow = factory.create(YELLOW);
    private final MoveStrategy blue = factory.create(BLUE);

    // Red (Aggressive)

    @Test
    void redChoosesPieceThatCanCaptureOpponent() {
        GameSnapshot snapshot = new SnapshotFixture()
                .onMainPath(RED, 1, 10)
                .onMainPath(RED, 2, 20)
                .onMainPath(YELLOW, 1, 14)
                .build();

        assertEquals(PieceChoice.of(1), red.choosePiece(snapshot, 4, ALL_PIECES));
    }

    @Test
    void redPrefersNotMovingFromBaseWhenCapturePossible() {
        GameSnapshot snapshot = new SnapshotFixture()
                .onMainPath(RED, 1, 10)
                .onMainPath(YELLOW, 1, 16)
                .build();

        assertFalse(red.prefersMoveFromBase(snapshot));
    }

    @Test
    void redBringsOutAnotherPieceOnSixWhenSixCannotCapture() {
        GameSnapshot snapshot = new SnapshotFixture()
                .onMainPath(RED, 1, 10)
                .onMainPath(YELLOW, 1, 30)
                .build();

        assertTrue(red.prefersMoveFromBase(snapshot));
    }

    @Test
    void redBringsOutPieceOnSixWhenNoPieceIsOnThePath() {
        assertTrue(red.prefersMoveFromBase(new SnapshotFixture().build()));
    }

    // Replaces the A1 Mockito test that verified a call to Board.advance: strategies no longer
    // see a Board, so the test now checks the outcome of the landing calculation instead.
    @Test
    void redSeesCaptureAcrossTheEndOfTheMainPathClockwise() {
        GameSnapshot snapshot = new SnapshotFixture()
                .onMainPath(RED, 1, 20)
                .onMainPath(RED, 2, 50)
                .onMainPath(YELLOW, 1, 2)
                .build();

        assertEquals(PieceChoice.of(2), red.choosePiece(snapshot, 4, ALL_PIECES));
    }

    @Test
    void redSeesCaptureAcrossTheStartOfTheMainPathCounterclockwise() {
        GameSnapshot snapshot = new SnapshotFixture()
                .onMainPath(RED, 1, 20)
                .onMainPath(RED, 2, 1, Direction.COUNTER_CLOCKWISE)
                .onMainPath(GREEN, 1, 50)
                .build();

        assertEquals(PieceChoice.of(2), red.choosePiece(snapshot, 3, ALL_PIECES));
    }

    @Test
    void redOnlyChoosesAmongCandidates() {
        // Rule 7 fallback: R1 could capture but was rejected, so it is not a candidate.
        GameSnapshot snapshot = new SnapshotFixture()
                .onMainPath(RED, 1, 10)
                .onMainPath(RED, 2, 20)
                .onMainPath(YELLOW, 1, 14)
                .build();

        assertEquals(PieceChoice.of(2), red.choosePiece(snapshot, 4, List.of(2, 3, 4)));
    }

    // Yellow (Winning)

    @Test
    void yellowAlwaysPrefersMovingFromBase() {
        assertTrue(yellow.prefersMoveFromBase(new SnapshotFixture().build()));
    }

    @Test
    void yellowChoosesPieceClosestToHome() {
        GameSnapshot snapshot = new SnapshotFixture()
                .onMainPath(YELLOW, 1, 5)
                .onMainPath(YELLOW, 2, 45)
                .build();

        assertEquals(PieceChoice.of(2), yellow.choosePiece(snapshot, 3, ALL_PIECES));
    }

    // Green (Blocking)

    @Test
    void greenPrefersMoveFromBaseUnlessBlockPossible() {
        assertTrue(green.prefersMoveFromBase(new SnapshotFixture().build()));
    }

    @Test
    void greenStaysOnBoardWhenSixFormsBlock() {
        GameSnapshot snapshot = new SnapshotFixture()
                .onMainPath(GREEN, 1, 10)
                .onMainPath(GREEN, 2, 16)
                .build();

        assertFalse(green.prefersMoveFromBase(snapshot));
    }

    @Test
    void greenChoosesPieceThatFormsBlock() {
        GameSnapshot snapshot = new SnapshotFixture()
                .onMainPath(GREEN, 1, 10)
                .onMainPath(GREEN, 2, 14)
                .build();

        assertEquals(PieceChoice.of(1), green.choosePiece(snapshot, 4, ALL_PIECES));
    }

    // Blue (Cyclic)

    @Test
    void blueRotatesThroughPiecesEachTurn() {
        SnapshotFixture fixture = new SnapshotFixture()
                .onMainPath(BLUE, 1, 10)
                .onMainPath(BLUE, 2, 20);

        PieceChoice first = blue.choosePiece(fixture.build(), 3, ALL_PIECES);
        PieceChoice second = blue.choosePiece(fixture.memo(BLUE, first.memo().getAsInt()).build(), 3, ALL_PIECES);

        assertEquals(OptionalInt.of(1), first.piece());
        assertEquals(OptionalInt.of(2), second.piece());
    }

    @Test
    void blueSkipsHomePiecesAndContinuesTheCycle() {
        // B2 is Home; B1, B3 and B4 are on the board
        SnapshotFixture fixture = new SnapshotFixture()
                .onMainPath(BLUE, 1, 10)
                .home(BLUE, 2)
                .onMainPath(BLUE, 3, 20)
                .onMainPath(BLUE, 4, 30);

        PieceChoice first = blue.choosePiece(fixture.build(), 3, ALL_PIECES);
        PieceChoice second = blue.choosePiece(fixture.memo(BLUE, first.memo().getAsInt()).build(), 3, ALL_PIECES);
        PieceChoice third = blue.choosePiece(fixture.memo(BLUE, second.memo().getAsInt()).build(), 3, ALL_PIECES);

        // B1, then B2 is skipped, so B3, then B4
        assertEquals(OptionalInt.of(1), first.piece());
        assertEquals(OptionalInt.of(3), second.piece());
        assertEquals(OptionalInt.of(4), third.piece());
    }

    @Test
    void blueStillChoosesCyclePieceThatCannotMove() {
        // B1 on homepath4 cannot use a 3 (exact roll needed); B2 could move.
        // Blue keeps to its cycle; the Game then skips the turn.
        GameSnapshot snapshot = new SnapshotFixture()
                .onHomeStraight(BLUE, 1, 4)
                .onMainPath(BLUE, 2, 20)
                .build();

        assertEquals(OptionalInt.of(1), blue.choosePiece(snapshot, 3, ALL_PIECES).piece());
        assertFalse(blue.triesOtherPiecesWhenBlocked());
    }

    @Test
    void bluePrefersMovingFromBase() {
        assertTrue(blue.prefersMoveFromBase(new SnapshotFixture().build()));
    }

    // Decider memo

    @Test
    void blueReadsItsCyclePositionFromTheMemoAndReturnsTheNextOne() {
        SnapshotFixture fixture = new SnapshotFixture()
                .onMainPath(BLUE, 1, 10)
                .onMainPath(BLUE, 2, 20)
                .onMainPath(BLUE, 3, 30)
                .memo(BLUE, 2);

        PieceChoice choice = blue.choosePiece(fixture.build(), 3, ALL_PIECES);

        assertEquals(OptionalInt.of(3), choice.piece());
        assertEquals(OptionalInt.of(3), choice.memo());
    }

    @Test
    void blueCycleWrapsFromLastPieceToFirst() {
        SnapshotFixture fixture = new SnapshotFixture()
                .onMainPath(BLUE, 1, 10)
                .onMainPath(BLUE, 4, 30)
                .memo(BLUE, 3);

        PieceChoice choice = blue.choosePiece(fixture.build(), 3, ALL_PIECES);

        assertEquals(OptionalInt.of(4), choice.piece());
        assertEquals(OptionalInt.of(0), choice.memo());
    }

    @Test
    void blueCaptureLeavesTheCyclePositionUnchanged() {
        // B2 has no capture yet and lands on Yellow with a 3, so it moves out of turn.
        GameSnapshot snapshot = new SnapshotFixture()
                .onMainPath(BLUE, 1, 10)
                .onMainPath(BLUE, 2, 20)
                .onMainPath(YELLOW, 1, 23)
                .build();

        PieceChoice choice = blue.choosePiece(snapshot, 3, ALL_PIECES);

        assertEquals(PieceChoice.of(2), choice);
        assertTrue(choice.memo().isEmpty());
    }

    @Test
    void blueWithNoPieceOnTheBoardChoosesNothing() {
        assertEquals(PieceChoice.none(), blue.choosePiece(new SnapshotFixture().build(), 3, ALL_PIECES));
    }

    @Test
    void redGreenAndYellowLeaveTheMemoUnused() {
        GameSnapshot snapshot = new SnapshotFixture()
                .onMainPath(RED, 1, 30)
                .onMainPath(GREEN, 1, 40)
                .onMainPath(YELLOW, 1, 5)
                .build();

        assertTrue(red.choosePiece(snapshot, 3, ALL_PIECES).memo().isEmpty());
        assertTrue(green.choosePiece(snapshot, 3, ALL_PIECES).memo().isEmpty());
        assertTrue(yellow.choosePiece(snapshot, 3, ALL_PIECES).memo().isEmpty());
    }
}
