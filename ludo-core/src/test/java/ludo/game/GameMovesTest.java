package ludo.game;

import ludo.players.SnapshotStrategyDecider;
import ludo.shared.Direction;
import ludo.shared.PlayerColor;
import ludo.piece.Piece;
import ludo.player.Player;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.OutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Single-roll rule checks: each test places pieces on the board, plays one roll
 * (or one turn) and inspects the resulting position and messages.
 */
class GameMovesTest {

    private PrintStream originalOut;
    private Queue<Integer> randomValues;
    private Game game;
    private final List<String> messages = new ArrayList<>();

    @BeforeEach
    void setUp() {
        originalOut = System.out;
        System.setOut(new PrintStream(OutputStream.nullOutputStream()));
        randomValues = new LinkedList<>();
        game = new GameBuilder().withMoveDecider(new SnapshotStrategyDecider()).withRandomSource(bound -> randomValues.isEmpty() ? 0 : randomValues.poll()).build();
        game.addObserver((event, message) -> messages.add(message.trim()));
    }

    @AfterEach
    void restoreConsole() {
        System.setOut(originalOut);
    }

    // Rule 7: fallback to another piece

    @Test
    void blockedPieceFallsBackToPlayersOtherPiece() {
        // Yellow's strategy prefers Y1 (closest to home), but a Red block sits right in front of it
        Piece y1 = place(PlayerColor.YELLOW, 1, 40, Direction.CLOCKWISE);
        Piece y2 = place(PlayerColor.YELLOW, 2, 10, Direction.CLOCKWISE);
        place(PlayerColor.RED, 1, 41, Direction.CLOCKWISE);
        place(PlayerColor.RED, 2, 41, Direction.CLOCKWISE);

        game.playRoll(PlayerColor.YELLOW, 3);

        assertEquals(40, y1.getMainPathPosition());
        assertEquals(13, y2.getMainPathPosition());
        assertTrue(messages.stream().noneMatch(m -> m.contains("Ignoring the throw")));
    }

    @Test
    void fullMoveOfAnotherPieceIsPreferredOverPartialMoveUpToBlock() {
        Piece y1 = place(PlayerColor.YELLOW, 1, 40, Direction.CLOCKWISE);
        Piece y2 = place(PlayerColor.YELLOW, 2, 10, Direction.CLOCKWISE);
        place(PlayerColor.RED, 1, 44, Direction.CLOCKWISE);
        place(PlayerColor.RED, 2, 44, Direction.CLOCKWISE);

        game.playRoll(PlayerColor.YELLOW, 5);

        assertEquals(40, y1.getMainPathPosition());
        assertEquals(15, y2.getMainPathPosition());
    }

    @Test
    void throwIsIgnoredOnlyWhenNoPieceCanMove() {
        place(PlayerColor.YELLOW, 1, 40, Direction.CLOCKWISE);
        place(PlayerColor.RED, 1, 41, Direction.CLOCKWISE);
        place(PlayerColor.RED, 2, 41, Direction.CLOCKWISE);

        MoveResult result = game.playRoll(PlayerColor.YELLOW, 3);

        assertFalse(result.isMoved());
        assertTrue(messages.contains("Yellow does not have other pieces in the board to move instead of the blocked piece."
                + " Ignoring the throw and moving on to the next player."));
    }

    @Test
    void blueKeepsToItsCycleAndSkipsWhenCycledPieceIsBlocked() {
        Piece b1 = place(PlayerColor.BLUE, 1, 40, Direction.CLOCKWISE);
        Piece b2 = place(PlayerColor.BLUE, 2, 20, Direction.CLOCKWISE);
        place(PlayerColor.RED, 1, 41, Direction.CLOCKWISE);
        place(PlayerColor.RED, 2, 41, Direction.CLOCKWISE);

        MoveResult result = game.playRoll(PlayerColor.BLUE, 3);

        assertFalse(result.isMoved());
        assertEquals(40, b1.getMainPathPosition());
        assertEquals(20, b2.getMainPathPosition());
    }

    // T-3: partial move up to a block

    @Test
    void pieceMovesUpToCellBeforeBlockWhenNothingElseCanMove() {
        Piece y1 = place(PlayerColor.YELLOW, 1, 40, Direction.CLOCKWISE);
        place(PlayerColor.RED, 1, 44, Direction.CLOCKWISE);
        place(PlayerColor.RED, 2, 44, Direction.CLOCKWISE);

        game.playRoll(PlayerColor.YELLOW, 5);

        assertEquals(43, y1.getMainPathPosition());
        assertTrue(messages.stream().anyMatch(m -> m.endsWith(
                "Moved the piece to square 43 which is the cell before the block.")));
    }

    // T-4 / T-5: block movement

    @Test
    void sameDirectionBlockMovesAsUnitByRollDividedBySize() {
        Piece g1 = place(PlayerColor.GREEN, 1, 10, Direction.CLOCKWISE);
        Piece g2 = place(PlayerColor.GREEN, 2, 10, Direction.CLOCKWISE);

        game.playRoll(PlayerColor.GREEN, 5);   // 5 / 2 = 2 cells each

        assertEquals(12, g1.getMainPathPosition());
        assertEquals(12, g2.getMainPathPosition());
    }

    @Test
    void mixedBlockMovesInDirectionOfLongestDistanceAndPiecesKeepTheirOwnDirection() {
        // Green approach is 37: clockwise from 10 is 27 cells, counterclockwise is 25
        Piece g1 = place(PlayerColor.GREEN, 1, 10, Direction.CLOCKWISE);
        Piece g2 = place(PlayerColor.GREEN, 2, 10, Direction.COUNTER_CLOCKWISE);

        game.playRoll(PlayerColor.GREEN, 4);

        assertEquals(12, g1.getMainPathPosition());
        assertEquals(12, g2.getMainPathPosition());
        assertEquals(Direction.CLOCKWISE, g1.getDirection());
        assertEquals(Direction.COUNTER_CLOCKWISE, g2.getDirection());
    }

    // T-8: block capture

    @Test
    void blockLandingOnSameSizeBlockCapturesIt() {
        Piece g1 = place(PlayerColor.GREEN, 1, 10, Direction.CLOCKWISE);
        Piece g2 = place(PlayerColor.GREEN, 2, 10, Direction.CLOCKWISE);
        Piece r1 = place(PlayerColor.RED, 1, 12, Direction.CLOCKWISE);
        Piece r2 = place(PlayerColor.RED, 2, 12, Direction.CLOCKWISE);

        MoveResult result = game.playRoll(PlayerColor.GREEN, 4);

        assertTrue(result.isCaptured());
        assertEquals(12, g1.getMainPathPosition());
        assertEquals(12, g2.getMainPathPosition());
        assertTrue(r1.isAtBase());
        assertTrue(r2.isAtBase());
        assertEquals(1, g1.getCaptureCount());
        assertEquals(1, g2.getCaptureCount());
    }

    @Test
    void blockCannotLandOnOpposingBlockOfDifferentSize() {
        Piece g1 = place(PlayerColor.GREEN, 1, 10, Direction.CLOCKWISE);
        Piece g2 = place(PlayerColor.GREEN, 2, 10, Direction.CLOCKWISE);
        Piece r1 = place(PlayerColor.RED, 1, 12, Direction.CLOCKWISE);
        place(PlayerColor.RED, 2, 12, Direction.CLOCKWISE);
        place(PlayerColor.RED, 3, 12, Direction.CLOCKWISE);

        game.playRoll(PlayerColor.GREEN, 4);

        assertEquals(12, r1.getMainPathPosition());
        assertEquals(11, g1.getMainPathPosition());
        assertEquals(11, g2.getMainPathPosition());
    }

    @Test
    void seed4DeadlockIsBrokenByBlockCaptureOnRollOfTwo() {
        // Seed 4, round 300: Blue B3+B4 on 2 counterclockwise, Yellow Y3+Y4 on 1 clockwise
        game.playerOf(PlayerColor.BLUE).getPieces().get(0).reachHome();
        game.playerOf(PlayerColor.BLUE).getPieces().get(1).reachHome();
        Piece b3 = place(PlayerColor.BLUE, 3, 2, Direction.COUNTER_CLOCKWISE);
        Piece b4 = place(PlayerColor.BLUE, 4, 2, Direction.COUNTER_CLOCKWISE);
        Piece y3 = place(PlayerColor.YELLOW, 3, 1, Direction.CLOCKWISE);
        Piece y4 = place(PlayerColor.YELLOW, 4, 1, Direction.CLOCKWISE);

        MoveResult result = game.playRoll(PlayerColor.BLUE, 2);   // 2 / 2 = 1 cell

        assertTrue(result.isCaptured());
        assertEquals(1, b3.getMainPathPosition());
        assertEquals(1, b4.getMainPathPosition());
        assertTrue(y3.isAtBase());
        assertTrue(y4.isAtBase());
    }

    // T-6: three sixes break blocks

    @Test
    void threeSixesPassTheTurnAndBreakTheBlock() throws InterruptedException {
        Piece y1 = place(PlayerColor.YELLOW, 1, 10, Direction.CLOCKWISE);
        Piece y2 = place(PlayerColor.YELLOW, 2, 10, Direction.CLOCKWISE);
        place(PlayerColor.YELLOW, 3, 30, Direction.CLOCKWISE);
        place(PlayerColor.YELLOW, 4, 45, Direction.CLOCKWISE);
        randomValues.addAll(List.of(5, 5, 5));   // dice: 6, 6, 6

        game.playTurn(PlayerColor.YELLOW);

        assertTrue(messages.contains("Yellow rolled six three times consecutively. Turn passed."));
        assertNotEquals(y1.getMainPathPosition(), y2.getMainPathPosition());
        assertEquals(Direction.CLOCKWISE, y2.getDirection());
    }

    @Test
    void tripleSixRuleBreaksEveryBlockAndPiecesMoveInTheirOwnDirection() {
        Piece y1 = place(PlayerColor.YELLOW, 1, 10, Direction.CLOCKWISE);
        Piece y2 = place(PlayerColor.YELLOW, 2, 10, Direction.COUNTER_CLOCKWISE);
        Piece y3 = place(PlayerColor.YELLOW, 3, 30, Direction.CLOCKWISE);
        Piece y4 = place(PlayerColor.YELLOW, 4, 30, Direction.CLOCKWISE);

        game.handleTripleSixRule(game.playerOf(PlayerColor.YELLOW));

        assertEquals(10, y1.getMainPathPosition());
        assertEquals(4, y2.getMainPathPosition());   // 10 - 6, its own counterclockwise direction
        assertEquals(30, y3.getMainPathPosition());
        assertEquals(36, y4.getMainPathPosition());
    }

    // Helpers

    private Piece place(PlayerColor color, int number, int cell, Direction direction) {
        Player player = game.playerOf(color);
        Piece piece = player.getPieces().get(number - 1);
        piece.placeOnStart();
        piece.setMainPathPosition(cell);
        piece.setDirection(direction);
        return piece;
    }
}
