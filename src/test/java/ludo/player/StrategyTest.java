package ludo.player;

import ludo.board.Board;
import ludo.board.PlayerColor;
import ludo.piece.Direction;
import ludo.piece.Piece;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StrategyTest {

    private Board board;

    @BeforeEach
    void setUp() {
        board = new Board(bound -> 0);
    }

    // Red (Aggressive)

    @Test
    void redChoosesPieceThatCanCaptureOpponent() {
        // Arrange
        RedPlayer red = new RedPlayer();
        Piece r1 = red.getPieces().get(0);
        Piece r2 = red.getPieces().get(1);

        r1.placeOnStart();
        r1.setMainPathPosition(10);
        r2.placeOnStart();
        r2.setMainPathPosition(20);

        Piece opponent = opponentPieceAt(PlayerColor.YELLOW, 14);
        List<Piece> all = Arrays.asList(r1, r2, opponent);

        // Act
        Piece chosen = red.choosePiece(all, board, 4);

        // Assert
        assertEquals(r1, chosen);
    }

    @Test
    void redPrefersNotMovingFromBaseWhenCapturePossible() {
        // Arrange
        RedPlayer red = new RedPlayer();
        Piece r1 = red.getPieces().get(0);
        r1.placeOnStart();
        r1.setMainPathPosition(10);

        Piece opponent = opponentPieceAt(PlayerColor.YELLOW, 16);
        List<Piece> all = Arrays.asList(r1, opponent);

        // Act & Assert
        assertFalse(red.prefersMoveFromBase(all, board));
    }

    // Communication-based: mock Board only here so verify() can inspect the call.
    @Test
    void redStrategy_callsAdvance_whenCheckingForCaptures() {
        // Arrange
        Board mockBoard = mock(Board.class);
        RedPlayer red = new RedPlayer();
        Piece r1 = red.getPieces().get(0);
        r1.placeOnStart();
        r1.setMainPathPosition(10);

        Piece opponent = opponentPieceAt(PlayerColor.YELLOW, 14);
        List<Piece> all = Arrays.asList(r1, opponent);
        when(mockBoard.advance(10, 4, Direction.CLOCKWISE)).thenReturn(14);

        // Act
        red.choosePiece(all, mockBoard, 4);

        // Assert
        verify(mockBoard).advance(10, 4, Direction.CLOCKWISE);
    }

    // Yellow (Winning)
    @Test
    void yellowAlwaysPrefersMovingFromBase() {
        YellowPlayer yellow = new YellowPlayer();
        List<Piece> all = yellow.getPieces();
        assertTrue(yellow.prefersMoveFromBase(all, board));
    }

    @Test
    void yellowChoosesPieceClosestToHome() {
        // Arrange
        YellowPlayer yellow = new YellowPlayer();
        Piece y1 = yellow.getPieces().get(0);
        Piece y2 = yellow.getPieces().get(1);

        y1.placeOnStart();
        y1.setMainPathPosition(5);
        y2.placeOnStart();
        y2.setMainPathPosition(45);

        List<Piece> all = Arrays.asList(y1, y2);

        // Act
        Piece chosen = yellow.choosePiece(all, board, 3);

        // Assert
        assertEquals(y2, chosen);
    }

    // Green (Blocking)

    @Test
    void greenPrefersMoveFromBaseUnlessBlockPossible() {
        GreenPlayer green = new GreenPlayer();
        List<Piece> all = green.getPieces();
        assertTrue(green.prefersMoveFromBase(all, board));
    }

    @Test
    void greenChoosesPieceThatFormsBlock() {
        // Arrange
        GreenPlayer green = new GreenPlayer();
        Piece g1 = green.getPieces().get(0);
        Piece g2 = green.getPieces().get(1);

        g1.placeOnStart();
        g1.setMainPathPosition(10);
        g2.placeOnStart();
        g2.setMainPathPosition(14);

        List<Piece> all = Arrays.asList(g1, g2);

        // Act
        Piece chosen = green.choosePiece(all, board, 4);

        // Assert
        assertEquals(g1, chosen);
    }

    // Blue (Cyclic)

    @Test
    void blueRotatesThroughPiecesEachTurn() {
        // Arrange
        BluePlayer blue = new BluePlayer();
        Piece b1 = blue.getPieces().get(0);
        Piece b2 = blue.getPieces().get(1);

        b1.placeOnStart();
        b1.setMainPathPosition(10);
        b2.placeOnStart();
        b2.setMainPathPosition(20);

        List<Piece> all = Arrays.asList(b1, b2);

        // Act
        Piece first = blue.choosePiece(all, board, 3);
        Piece second = blue.choosePiece(all, board, 3);

        // Assert
        assertNotEquals(first, second);
    }

    @Test
    void bluePrefersMovingFromBase() {
        BluePlayer blue = new BluePlayer();
        List<Piece> all = blue.getPieces();
        assertTrue(blue.prefersMoveFromBase(all, board));
    }

    // Helpers

    private Piece opponentPieceAt(PlayerColor color, int cell) {
        Piece p = new Piece(color, 1);
        p.placeOnStart();
        p.setMainPathPosition(cell);
        return p;
    }
}
