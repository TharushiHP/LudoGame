package ludo.player;

import ludo.board.PlayerColor;
import ludo.piece.Piece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlayerTest {

    private Player redPlayer;
    private Player yellowPlayer;

    @BeforeEach
    void setUp() {
        redPlayer    = new RedPlayer();
        yellowPlayer = new YellowPlayer();
    }

    @Test
    void playerStartsWithFourPieces() {
        assertEquals(4, redPlayer.getPieces().size());
    }

    @Test
    void allPiecesAreAtBaseAtStart() {
        assertTrue(redPlayer.getPieces().stream().allMatch(Piece::isAtBase));
    }

    @Test
    void countPiecesOnBoardIsZeroAtStart() {
        assertEquals(0, redPlayer.countPiecesOnBoard());
    }

    @Test
    void countPiecesAtBaseIsFourAtStart() {
        assertEquals(4, redPlayer.countPiecesAtBase());
    }

    @Test
    void hasAllPiecesHomeReturnsFalseAtStart() {
        assertFalse(redPlayer.hasAllPiecesHome());
    }

    @Test
    void hasAllPiecesHomeReturnsTrueWhenAllHome() {
        redPlayer.getPieces().forEach(Piece::reachHome);
        assertTrue(redPlayer.hasAllPiecesHome());
    }

    @Test
    void consecutiveSixesTrackedCorrectly() {
        redPlayer.recordSix();
        redPlayer.recordSix();
        assertEquals(2, redPlayer.getConsecutiveSixes());
    }

    @Test
    void hasTripleConsecutiveSixesAfterThreeSixes() {
        redPlayer.recordSix();
        redPlayer.recordSix();
        redPlayer.recordSix();
        assertTrue(redPlayer.hasTripleConsecutiveSixes());
    }

    @Test
    void resetConsecutiveSixesClearsCount() {
        redPlayer.recordSix();
        redPlayer.recordSix();
        redPlayer.resetConsecutiveSixes();
        assertFalse(redPlayer.hasTripleConsecutiveSixes());
    }

    @Test
    void hasBlockadeReturnsFalseWithNoPiecesOnBoard() {
        assertFalse(redPlayer.hasBlockade());
    }

    @Test
    void hasBlockadeReturnsTrueWhenTwoPiecesSameCell() {
        redPlayer.getPieces().get(0).placeOnStart();
        redPlayer.getPieces().get(0).setMainPathPosition(10);
        redPlayer.getPieces().get(1).placeOnStart();
        redPlayer.getPieces().get(1).setMainPathPosition(10);

        assertTrue(redPlayer.hasBlockade());
    }

    @Test
    void describeStateShowsCorrectPieceCounts() {
        String state = yellowPlayer.describeState();
        assertTrue(state.contains("0/4 on pieces on the board"));
        assertTrue(state.contains("4/4 pieces on the base"));
    }

    @Test
    void finishPositionDefaultsToZero() {
        assertEquals(0, redPlayer.getFinishPosition());
    }

    @Test
    void finishPositionCanBeSet() {
        redPlayer.setFinishPosition(1);
        assertEquals(1, redPlayer.getFinishPosition());
    }

    @Test
    void redPlayerColorIsRed() {
        assertEquals(PlayerColor.RED, redPlayer.getColor());
    }

    @Test
    void yellowPlayerColorIsYellow() {
        assertEquals(PlayerColor.YELLOW, yellowPlayer.getColor());
    }
}
