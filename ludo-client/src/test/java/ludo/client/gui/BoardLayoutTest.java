package ludo.client.gui;

import ludo.shared.BoardConstants;
import ludo.shared.PathMath;
import ludo.shared.PlayerColor;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/** The board drawn by the GUI is the board of Figure 1 in the brief. */
class BoardLayoutTest {

    private final BoardLayout layout = new BoardLayout();

    @Test
    void all52CellsAreOnDistinctSquares() {
        Set<GridPos> squares = new HashSet<>();
        for (int cell = 0; cell < BoardConstants.MAIN_PATH_SIZE; cell++)
            assertTrue(squares.add(layout.cell(cell)), "cell " + cell + " shares a square");
        assertEquals(52, squares.size());
    }

    @Test
    void eachCellTouchesTheNextOneIncluding51To0() {
        for (int cell = 0; cell < BoardConstants.MAIN_PATH_SIZE; cell++) {
            int next = (cell + 1) % BoardConstants.MAIN_PATH_SIZE;
            assertTrue(layout.cell(cell).touches(layout.cell(next)),
                    "cell " + cell + " " + layout.cell(cell) + " does not touch cell " + next + " " + layout.cell(next));
        }
    }

    /** The path turns each inner corner of Figure 1 with one diagonal step; every other step is straight. */
    @Test
    void onlyTheFourInnerCornerStepsAreDiagonal() {
        List<Integer> diagonal = new ArrayList<>();
        for (int cell = 0; cell < BoardConstants.MAIN_PATH_SIZE; cell++)
            if (layout.cell(cell).isDiagonalTo(layout.cell((cell + 1) % BoardConstants.MAIN_PATH_SIZE)))
                diagonal.add(cell);
        assertEquals(List.of(4, 17, 30, 43), diagonal);
    }

    @Test
    void startAndApproachCellsAreWhereFigure1PutsThem() {
        assertEquals(new GridPos(1, 8), layout.cell(PathMath.startCell(PlayerColor.YELLOW)));
        assertEquals(new GridPos(0, 7), layout.cell(PathMath.approachCell(PlayerColor.YELLOW)));
        assertEquals(new GridPos(8, 13), layout.cell(PathMath.startCell(PlayerColor.BLUE)));
        assertEquals(new GridPos(7, 14), layout.cell(PathMath.approachCell(PlayerColor.BLUE)));
        assertEquals(new GridPos(13, 6), layout.cell(PathMath.startCell(PlayerColor.RED)));
        assertEquals(new GridPos(14, 7), layout.cell(PathMath.approachCell(PlayerColor.RED)));
        assertEquals(new GridPos(6, 1), layout.cell(PathMath.startCell(PlayerColor.GREEN)));
        assertEquals(new GridPos(7, 0), layout.cell(PathMath.approachCell(PlayerColor.GREEN)));
        assertEquals(new GridPos(0, 8), layout.cell(51));
    }

    @Test
    void pathFollowsFigure1FromCell0() {
        for (int row = 1; row <= 5; row++)
            assertEquals(new GridPos(row, 8), layout.cell(row - 1), "down column 8");
        for (int col = 9; col <= 14; col++)
            assertEquals(new GridPos(6, col), layout.cell(col - 4), "along row 6");
        assertEquals(new GridPos(8, 14), layout.cell(12));
        for (int col = 13; col >= 9; col--)
            assertEquals(new GridPos(8, col), layout.cell(13 + (13 - col)), "left along row 8");
        for (int row = 9; row <= 14; row++)
            assertEquals(new GridPos(row, 8), layout.cell(18 + row - 9), "down column 8");
    }

    @Test
    void homeStraightsAreWhereFigure1PutsThem() {
        for (int i = 0; i < 5; i++) {
            assertEquals(new GridPos(1 + i, 7), layout.homeStraight(PlayerColor.YELLOW, i));
            assertEquals(new GridPos(7, 13 - i), layout.homeStraight(PlayerColor.BLUE, i));
            assertEquals(new GridPos(13 - i, 7), layout.homeStraight(PlayerColor.RED, i));
            assertEquals(new GridPos(7, 1 + i), layout.homeStraight(PlayerColor.GREEN, i));
        }
    }

    @Test
    void eachHomeStraightLeadsFromItsApproachCellToTheCentre() {
        GridPos centre = new GridPos(7, 7);
        for (PlayerColor colour : PlayerColor.values()) {
            GridPos previous = layout.cell(PathMath.approachCell(colour));
            for (int i = 0; i < BoardConstants.HOME_STRAIGHT_SIZE; i++) {
                GridPos square = layout.homeStraight(colour, i);
                assertTrue(previous.touches(square) && !previous.isDiagonalTo(square),
                        colour + " home straight " + i + " does not follow " + previous);
                assertEquals(distance(previous, centre) - 1, distance(square, centre),
                        colour + " home straight " + i + " does not move towards the centre");
                previous = square;
            }
            assertEquals(2, distance(previous, centre), colour + "'s last square must touch the Home square");
        }
    }

    @Test
    void pathHomeStraightsBasesAndCentreNeverOverlap() {
        Set<GridPos> used = new HashSet<>();
        for (int cell = 0; cell < 52; cell++)
            used.add(layout.cell(cell));
        for (PlayerColor colour : PlayerColor.values())
            for (int i = 0; i < 5; i++)
                assertTrue(used.add(layout.homeStraight(colour, i)), colour + " home straight overlaps");
        for (GridPos square : used) {
            assertFalse(BoardLayout.CENTRE.contains(square), square + " is inside Home");
            for (PlayerColor colour : PlayerColor.values())
                assertFalse(layout.base(colour).contains(square), square + " is inside " + colour + "'s base");
        }
        assertEquals(52 + 20, used.size());
        // 72 path and home-straight squares + 9 Home squares + 4 bases of 6x6 fill the 15x15 grid exactly.
        Set<GridPos> everything = new HashSet<>(used);
        for (int row = 0; row < BoardLayout.SIZE; row++)
            for (int col = 0; col < BoardLayout.SIZE; col++) {
                GridPos p = new GridPos(row, col);
                if (BoardLayout.CENTRE.contains(p) || layout.baseAt(p).isPresent())
                    assertTrue(everything.add(p), p + " belongs to two areas");
            }
        assertEquals(BoardLayout.SIZE * BoardLayout.SIZE, everything.size());
    }

    @Test
    void basesSitBesideTheirStartCells() {
        for (PlayerColor colour : PlayerColor.values()) {
            GridPos start = layout.cell(PathMath.startCell(colour));
            boolean beside = false;
            for (int dr = -1; dr <= 1; dr++)
                for (int dc = -1; dc <= 1; dc++)
                    beside |= layout.base(colour).contains(new GridPos(start.row() + dr, start.col() + dc));
            assertTrue(beside, colour + "'s base is not next to its X");
        }
    }

    @Test
    void specialCellsComeFromTheRules() {
        assertEquals(new GridPos(6, 11), layout.cell(BoardConstants.ALPHA));
        assertEquals(new GridPos(14, 6), layout.cell(BoardConstants.BETA));
        assertEquals(new GridPos(5, 6), layout.cell(BoardConstants.GAMMA));
    }

    private static int distance(GridPos a, GridPos b) {
        return Math.abs(a.row() - b.row()) + Math.abs(a.col() - b.col());
    }
}
