package ludo.board;

import ludo.shared.BoardConstants;
import ludo.shared.Direction;
import ludo.shared.PlayerColor;
import ludo.piece.Piece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BoardTest {

    private Board board;

    @BeforeEach
    void setUp() {
        board = new Board(bound -> 0);
    }

    // advance()
    @Test
    void advanceClockwiseWrapsAround() {
        assertEquals(3, board.advance(50, 5, Direction.CLOCKWISE));
    }

    @Test
    void advanceClockwiseNormal() {
        assertEquals(14, board.advance(10, 4, Direction.CLOCKWISE));
    }

    @Test
    void advanceCounterClockwiseWrapsAround() {
        assertEquals(49, board.advance(2, 5, Direction.COUNTER_CLOCKWISE));
    }

    @Test
    void advanceCounterClockwiseNormal() {
        assertEquals(16, board.advance(20, 4, Direction.COUNTER_CLOCKWISE));
    }

    // computeMoveTarget()

    @Test
    void moveOnMainPathWhenNotNearApproach() {
        Piece p = yellowPieceAt(10);
        MoveTarget target = board.computeMoveTarget(p, 3);
        assertTrue(target.isMainPath());
        assertEquals(13, target.getPosition());
    }

    @Test
    void landOnApproachStaysOnMainPath() {
        Piece p = yellowPieceAt(47);
        MoveTarget target = board.computeMoveTarget(p, 3);
        assertTrue(target.isMainPath());
        assertEquals(BoardConstants.YELLOW_APPROACH, target.getPosition());
    }

    @Test
    void passingApproachEntersHomeStraight() {
        Piece p = yellowPieceAt(47);
        MoveTarget target = board.computeMoveTarget(p, 5);
        assertTrue(target.isHomeStraight());
        assertEquals(1, target.getPosition());
    }

    @Test
    void exactStepsToHomeFromApproachReachesHome() {
        // 5 home-straight cells, then Home: 6 steps from the approach cell
        Piece p = yellowPieceAt(BoardConstants.YELLOW_APPROACH);
        MoveTarget target = board.computeMoveTarget(p, BoardConstants.HOME_STRAIGHT_SIZE + 1);
        assertTrue(target.isHome());
    }

    @Test
    void overshootingHomeStraightReturnsOvershoot() {
        Piece p = yellowPieceAt(BoardConstants.YELLOW_APPROACH);
        MoveTarget target = board.computeMoveTarget(p, BoardConstants.HOME_STRAIGHT_SIZE + 2);
        assertTrue(target.isOvershoot());
    }

    @Test
    void piecePastHomeStraightCellMovesCorrectly() {
        Piece p = new Piece(PlayerColor.YELLOW, 1);
        p.placeOnStart();
        p.moveToHomePath(2);
        MoveTarget target = board.computeMoveTarget(p, 2);
        assertTrue(target.isHomeStraight());
        assertEquals(4, target.getPosition());
    }

    @Test
    void exactRollFromHomeStraightReachesHome() {
        Piece p = new Piece(PlayerColor.YELLOW, 1);
        p.placeOnStart();
        p.moveToHomePath(4);
        MoveTarget target = board.computeMoveTarget(p, 1);
        assertTrue(target.isHome());
    }

    // Block detection

    @Test
    void isOpponentBlockReturnsTrueWhenTwoOpponentsAtCell() {
        Piece r1 = redPieceAt(10);
        Piece r2 = redPieceAt(10);
        Piece yellow = yellowPieceAt(5);
        assertTrue(board.isOpponentBlock(PlayerColor.YELLOW, 10, Arrays.asList(r1, r2, yellow)));
    }

    @Test
    void isOpponentBlockReturnsFalseWhenOnlyOneOpponent() {
        Piece r1 = redPieceAt(10);
        Piece yellow = yellowPieceAt(5);
        assertFalse(board.isOpponentBlock(PlayerColor.YELLOW, 10, Arrays.asList(r1, yellow)));
    }

    @Test
    void hasSameColorBlockReturnsTrueForTwoSameColorPieces() {
        Piece r1 = redPieceAt(20);
        Piece r2 = redPieceAt(20);
        assertTrue(board.hasSameColorBlock(20, PlayerColor.RED, Arrays.asList(r1, r2)));
    }

    @Test
    void hasSameColorBlockReturnsFalseForSinglePiece() {
        Piece r1 = redPieceAt(20);
        assertFalse(board.hasSameColorBlock(20, PlayerColor.RED, Collections.singletonList(r1)));
    }

    // Cell queries

    @Test
    void piecesAtCellReturnsAllOccupants() {
        Piece p1 = yellowPieceAt(15);
        Piece p2 = redPieceAt(15);
        List<Piece> result = board.piecesAtCell(15, Arrays.asList(p1, p2));
        assertEquals(2, result.size());
    }

    @Test
    void opponentPiecesAtCellExcludesOwnColor() {
        Piece yellow = yellowPieceAt(15);
        Piece red = redPieceAt(15);
        List<Piece> result = board.opponentPiecesAtCell(PlayerColor.YELLOW, 15, Arrays.asList(yellow, red));
        assertEquals(1, result.size());
        assertEquals(PlayerColor.RED, result.get(0).getColor());
    }

    // Distance helper

    @Test
    void distanceToApproachIsZeroWhenAtApproach() {
        Piece p = yellowPieceAt(BoardConstants.YELLOW_APPROACH);
        assertEquals(0, board.distanceToApproach(p));
    }

    @Test
    void distanceToApproachIsCorrectForMidBoard() {
        Piece p = yellowPieceAt(40);
        int expected = (BoardConstants.YELLOW_APPROACH - 40 + 52) % 52;
        assertEquals(expected, board.distanceToApproach(p));
    }

    // Rule 10: approach -> homepath0..homepath4 (5 cells) -> Home, exact roll only.
    @Test
    void homeStraightHasFiveCellsFromApproach() {
        for (int steps = 1; steps <= BoardConstants.HOME_STRAIGHT_SIZE; steps++) {
            MoveTarget target = board.computeMoveTarget(yellowPieceAt(BoardConstants.YELLOW_APPROACH), steps);
            assertTrue(target.isHomeStraight(), "steps " + steps + " gave " + target);
            assertEquals(steps - 1, target.getPosition());
        }
    }

    @Test
    void sameTotalStepsReachSameHomeStraightCellWhetherStartingBeforeOrOnApproach() {
        MoveTarget fromBefore = board.computeMoveTarget(yellowPieceAt(47), 8);
        MoveTarget fromApproach = board.computeMoveTarget(yellowPieceAt(BoardConstants.YELLOW_APPROACH), 5);
        assertEquals(fromBefore.toString(), fromApproach.toString());
    }

    @Test
    void homeStraightCellsAreLabelledHomepath0ToHomepath4() {
        Piece p = yellowPieceAt(10);
        p.moveToHomePath(0);
        assertEquals("yellowhomepath0", p.positionLabel());
        p.moveToHomePath(4);
        assertEquals("yellowhomepath4", p.positionLabel());
    }

    // Helpers

    private Piece yellowPieceAt(int cell) {
        Piece p = new Piece(PlayerColor.YELLOW, 1);
        p.placeOnStart();
        p.setMainPathPosition(cell);
        return p;
    }

    private Piece redPieceAt(int cell) {
        Piece p = new Piece(PlayerColor.RED, 1);
        p.placeOnStart();
        p.setMainPathPosition(cell);
        return p;
    }
}
