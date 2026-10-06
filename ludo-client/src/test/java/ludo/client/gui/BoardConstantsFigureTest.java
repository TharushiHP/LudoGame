package ludo.client.gui;

import ludo.shared.BoardConstants;
import ludo.shared.Direction;
import ludo.shared.PathMath;
import ludo.shared.PlayerColor;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The game rules' cell numbers (BoardConstants) agree with Figure 1 of the brief. If one of these
 * fails, the rules and the drawn board disagree: fix the rules only after an approved change,
 * because the golden master depends on them.
 */
class BoardConstantsFigureTest {

    @Test
    void startCellsMatchFigure1() {
        assertEquals(0, PathMath.startCell(PlayerColor.YELLOW));
        assertEquals(13, PathMath.startCell(PlayerColor.BLUE));
        assertEquals(26, PathMath.startCell(PlayerColor.RED));
        assertEquals(39, PathMath.startCell(PlayerColor.GREEN));
    }

    @Test
    void approachCellsAreTwoCellsBeforeEachStart() {
        assertEquals(50, PathMath.approachCell(PlayerColor.YELLOW));
        assertEquals(11, PathMath.approachCell(PlayerColor.BLUE));
        assertEquals(24, PathMath.approachCell(PlayerColor.RED));
        assertEquals(37, PathMath.approachCell(PlayerColor.GREEN));
        for (PlayerColor colour : PlayerColor.values())
            assertEquals(PathMath.startCell(colour),
                    (PathMath.approachCell(colour) + 2) % BoardConstants.MAIN_PATH_SIZE, colour.display());
    }

    /** The brief: the 9th, 27th and 46th cells clockwise from Yellow's approach cell (which counts as zero). */
    @Test
    void alphaBetaGammaAreCountedFromYellowsApproachCell() {
        int yellowApproach = PathMath.approachCell(PlayerColor.YELLOW);
        assertEquals((yellowApproach + 9) % 52, BoardConstants.ALPHA);
        assertEquals((yellowApproach + 27) % 52, BoardConstants.BETA);
        assertEquals((yellowApproach + 46) % 52, BoardConstants.GAMMA);
        assertEquals(7, BoardConstants.ALPHA);
        assertEquals(25, BoardConstants.BETA);
        assertEquals(44, BoardConstants.GAMMA);
    }

    @Test
    void homeStraightStartsFromTheApproachCell() {
        for (PlayerColor colour : PlayerColor.values()) {
            int approach = PathMath.approachCell(colour);
            assertEquals(0, PathMath.stepsToApproach(approach, colour, Direction.CLOCKWISE), colour.display());
        }
        assertEquals(0, PathMath.homeStraightIndexAfter(-1, 1), "one step past the approach cell is square 0");
        assertEquals(5, BoardConstants.HOME_STRAIGHT_SIZE);
    }
}
