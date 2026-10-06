package ludo.client.gui.model;

import ludo.client.gui.model.TableLayout.Rect;
import ludo.shared.PlayerColor;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** The board and the player boxes always fit the window, without overlapping. */
class TableLayoutTest {

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({
            "1536, 780",   // 1920x1080 at 125 %, maximised (taskbar and title bar off)
            "1366, 700",   // 1366x768 at 100 %, maximised
            "1229, 693",   // 90 % player window on 1366x768
            "800, 600",
            "640, 480",
            "600, 900"     // tall
    })
    void boardAndBoxesFitWithoutOverlapping(int width, int height) {
        TableLayout t = TableLayout.of(width, height);
        Rect board = t.board();
        assertTrue(board.inside(width, height), "board " + board);
        assertEquals(board.width(), board.height(), 1e-9, "the board is square");
        assertEquals(width / 2.0, board.centreX(), 1e-6, "centred horizontally");
        assertEquals(height / 2.0, board.centreY(), 1e-6, "centred vertically");
        for (PlayerColor colour : PlayerColor.values()) {
            Rect box = t.box(colour);
            assertTrue(box.inside(width, height), colour + " box " + box + " is clipped");
            assertFalse(box.overlaps(board), colour + " box covers the board");
            for (PlayerColor other : PlayerColor.values())
                if (other != colour)
                    assertFalse(box.overlaps(t.box(other)), colour + " and " + other + " boxes overlap");
        }
    }

    @ParameterizedTest(name = "{0} x {1}")
    @CsvSource({"1536, 780", "1366, 700", "1229, 693", "800, 600", "640, 480", "600, 900"})
    void theInfoButtonFitsWithoutCoveringAnything(int width, int height) {
        TableLayout t = TableLayout.of(width, height);
        Rect info = t.info();
        assertTrue(info.inside(width, height), "info button " + info + " is clipped");
        assertFalse(info.overlaps(t.board()), "info button covers the board");
        for (PlayerColor colour : PlayerColor.values())
            assertFalse(info.overlaps(t.box(colour)), "info button covers the " + colour + " box");
        assertEquals(t.board().width() * TableLayout.INFO, info.width(), 1e-9);
    }

    @Test
    void eachBoxIsNextToItsOwnBase() {
        TableLayout t = TableLayout.of(1536, 780);
        Rect board = t.board();
        assertTrue(t.box(PlayerColor.GREEN).x() < board.x() && t.box(PlayerColor.GREEN).y() < board.centreY());
        assertTrue(t.box(PlayerColor.YELLOW).x() > board.centreX() && t.box(PlayerColor.YELLOW).y() < board.centreY());
        assertTrue(t.box(PlayerColor.RED).x() < board.x() && t.box(PlayerColor.RED).y() > board.centreY());
        assertTrue(t.box(PlayerColor.BLUE).x() > board.centreX() && t.box(PlayerColor.BLUE).y() > board.centreY());
    }

    @Test
    void theBoardIsAsLargeAsTheWindowAllows() {
        TableLayout wide = TableLayout.of(1536, 780);
        assertEquals(780 / (1 + 2 * TableLayout.GAP), wide.board().width(), 1e-6, "height-limited when wide");
        assertEquals(wide.board().width() / 15, wide.cell(), 1e-9);
    }
}
