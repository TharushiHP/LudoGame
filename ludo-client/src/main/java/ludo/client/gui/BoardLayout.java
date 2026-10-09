package ludo.client.gui;

import ludo.shared.BoardConstants;
import ludo.shared.PathMath;
import ludo.shared.PlayerColor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;


public final class BoardLayout {

    public static final int SIZE = 15;

    private static final int QUARTER = BoardConstants.MAIN_PATH_SIZE / 4;

    /** Yellow's quarter, path cells 0-12 (Figure 1). */
    private static final GridPos[] YELLOW_QUARTER = {
            // cells 0-4: Yellow's X, then down column 8
            new GridPos(1, 8), new GridPos(2, 8), new GridPos(3, 8), new GridPos(4, 8), new GridPos(5, 8),
            // cells 5-10: along row 6 to the right
            new GridPos(6, 9), new GridPos(6, 10), new GridPos(6, 11), new GridPos(6, 12), new GridPos(6, 13),
            new GridPos(6, 14),
            // cells 11-12: down the right edge (11 is Blue's approach circle, 12 the corner)
            new GridPos(7, 14), new GridPos(8, 14)
    };

    /** Yellow's home straight: column 7, rows 1-5, index 0 next to the approach cell. */
    private static final GridPos YELLOW_HOME_STRAIGHT_START = new GridPos(1, 7);

    /** Yellow's base: the 6x6 square in the top-right corner. */
    private static final GridRect YELLOW_BASE = new GridRect(0, 9, 6, 6);

    /** The Home (centre) square: rows and columns 6-8. */
    public static final GridRect CENTRE = new GridRect(6, 6, 3, 3);

    private final GridPos[] path = new GridPos[BoardConstants.MAIN_PATH_SIZE];

    public BoardLayout() {
        for (int cell = 0; cell < path.length; cell++)
            path[cell] = YELLOW_QUARTER[cell % QUARTER].rotateClockwise(cell / QUARTER);
    }

    /** Grid square of main-path cell 0-51. */
    public GridPos cell(int cell) {
        return path[cell];
    }

    /** Grid square of a colour's home-straight square, index 0 (next to the approach cell) to 4. */
    public GridPos homeStraight(PlayerColor colour, int index) {
        if (index < 0 || index >= BoardConstants.HOME_STRAIGHT_SIZE)
            throw new IllegalArgumentException("home-straight index " + index);
        GridPos yellow = new GridPos(YELLOW_HOME_STRAIGHT_START.row() + index, YELLOW_HOME_STRAIGHT_START.col());
        return yellow.rotateClockwise(quarterOf(colour));
    }

    /** The 6x6 base square of a colour. */
    public GridRect base(PlayerColor colour) {
        return YELLOW_BASE.rotateClockwise(quarterOf(colour));
    }

    /** Centre of a colour's base, in grid units: the white centre square of its cross (Figure 1). */
    public Spot baseCentre(PlayerColor colour) {
        GridRect base = base(colour);
        return new Spot(base.row() + base.height() / 2.0, base.col() + base.width() / 2.0);
    }

    /**
     * Centres of the four piece places in a colour's base, in grid units (row, col), where the
     * square (r, c) runs from r to r + 1. They are the four coloured arms of the cross in Figure 1,
     * one grid square each round the white centre: piece 1 top, 2 right, 3 bottom, 4 left. The
     * order is the same for every colour (not turned with the quarter), so "piece 1 is at the top"
     * holds on every base.
     */
    public List<Spot> baseSlots(PlayerColor colour) {
        Spot c = baseCentre(colour);
        return List.of(new Spot(c.row() - CROSS_ARM, c.col()), new Spot(c.row(), c.col() + CROSS_ARM),
                new Spot(c.row() + CROSS_ARM, c.col()), new Spot(c.row(), c.col() - CROSS_ARM));
    }

    /** Size of one square of a base's cross, in grid squares (Figure 1: the same as a path cell). */
    public static final double CROSS_ARM = 1.0;

    /** Centres of the four piece places in a colour's triangle of the Home square (grid units). */
    public List<Spot> homeSlots(PlayerColor colour) {
        // Below the "Home" label (row 6.1-6.4), so a token never hides it.
        List<Spot> yellow = List.of(new Spot(6.66, 6.98), new Spot(6.66, 7.5), new Spot(6.66, 8.02), new Spot(7.1, 7.5));
        return rotateAll(yellow, quarterOf(colour));
    }

    /**
     * The colour's triangle of the Home square, as its three corners (grid units). Yellow's is the
     * top triangle, at the end of its home straight.
     */
    public List<Spot> homeTriangle(PlayerColor colour) {
        List<Spot> yellow = List.of(new Spot(6, 6), new Spot(6, 9), new Spot(7.5, 7.5));
        return rotateAll(yellow, quarterOf(colour));
    }

    /** The main-path cell drawn on this square, if any. */
    public Optional<Integer> cellAt(GridPos pos) {
        for (int cell = 0; cell < path.length; cell++)
            if (path[cell].equals(pos))
                return Optional.of(cell);
        return Optional.empty();
    }

    /** The colour whose home straight covers this square, if any. */
    public Optional<PlayerColor> homeStraightAt(GridPos pos) {
        for (PlayerColor colour : PlayerColor.values())
            for (int i = 0; i < BoardConstants.HOME_STRAIGHT_SIZE; i++)
                if (homeStraight(colour, i).equals(pos))
                    return Optional.of(colour);
        return Optional.empty();
    }

    /** The colour whose base covers this square, if any. */
    public Optional<PlayerColor> baseAt(GridPos pos) {
        for (PlayerColor colour : PlayerColor.values())
            if (base(colour).contains(pos))
                return Optional.of(colour);
        return Optional.empty();
    }

    /**
     * How many quarter turns clockwise from Yellow's quarter this colour's quarter is, worked out from
     * its X cell: Yellow 0, Blue 1, Red 2, Green 3.
     */
    static int quarterOf(PlayerColor colour) {
        return PathMath.startCell(colour) / QUARTER;
    }

    private static List<Spot> rotateAll(List<Spot> spots, int quarterTurns) {
        List<Spot> rotated = new ArrayList<>();
        for (Spot s : spots)
            rotated.add(s.rotateClockwise(quarterTurns));
        return rotated;
    }

    /** A point on the board in grid units (row, col): square (r, c) covers r..r+1 and c..c+1. */
    public record Spot(double row, double col) {

        Spot rotateClockwise(int quarterTurns) {
            Spot p = this;
            for (int i = 0; i < quarterTurns; i++)
                p = new Spot(p.col, SIZE - p.row);
            return p;
        }
    }

    /** A rectangle of grid squares (Value Object). */
    public record GridRect(int row, int col, int height, int width) {

        public boolean contains(GridPos p) {
            return p.row() >= row && p.row() < row + height && p.col() >= col && p.col() < col + width;
        }

        GridRect rotateClockwise(int quarterTurns) {
            GridPos a = new GridPos(row, col).rotateClockwise(quarterTurns);
            GridPos b = new GridPos(row + height - 1, col + width - 1).rotateClockwise(quarterTurns);
            int top = Math.min(a.row(), b.row());
            int left = Math.min(a.col(), b.col());
            return new GridRect(top, left, Math.abs(a.row() - b.row()) + 1, Math.abs(a.col() - b.col()) + 1);
        }
    }
}
