package ludo.client.gui.model;

import ludo.client.gui.model.TableLayout.Rect;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

/** The winner box is centred over the board, and its close button sits inside its top-right corner. */
class WinnerBoxTest {

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"1536, 780", "1366, 700", "1229, 693", "800, 600", "640, 480", "600, 900"})
    void boxIsCentredOverTheBoardWithTheCloseButtonInItsCorner(int width, int height) {
        Rect board = TableLayout.of(width, height).board();
        for (boolean firstWinner : new boolean[]{true, false}) {
            WinnerBox winner = WinnerBox.of(board, firstWinner);
            Rect box = winner.box(), close = winner.close();
            assertEquals(board.centreX(), box.centreX(), 1e-6);
            assertEquals(board.centreY(), box.centreY(), 1e-6);
            assertTrue(box.x() >= board.x() && box.x() + box.width() <= board.x() + board.width(), "inside the board");
            assertTrue(close.x() >= box.x() && close.x() + close.width() <= box.x() + box.width());
            assertTrue(close.y() >= box.y() && close.y() + close.height() <= box.y() + box.height());
            assertTrue(close.centreX() > box.centreX() && close.centreY() < box.centreY(), "top-right corner");

            assertTrue(winner.onClose(close.centreX(), close.centreY()));
            assertFalse(winner.onClose(box.centreX(), box.centreY()), "the middle of the box is not the button");
            assertFalse(winner.onClose(close.x(), close.y()), "the button is round: its square's corner is outside");
        }
    }
}
