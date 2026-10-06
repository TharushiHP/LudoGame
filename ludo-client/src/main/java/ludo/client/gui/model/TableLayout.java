package ludo.client.gui.model;

import ludo.shared.PlayerColor;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Where the board and the four player boxes go in a window of a given size (pure geometry, no
 * Swing, unit-tested). The board is the largest square that fits once the player boxes have room
 * outside its corners: beside the board when the window is wide, above and below it when the
 * window is tall. Whichever gives the larger board wins. Each box sits next to its own base:
 * Green top-left, Yellow top-right, Red bottom-left, Blue bottom-right (Figure 1). A small round
 * "i" button for the symbol legend goes in a free spot round them ({@link #info()}).
 */
public final class TableLayout {

    /** Player box size as a fraction of the board side. */
    static final double BOX_WIDTH = 0.27;
    static final double BOX_HEIGHT = 0.17;
    /** Space between board and boxes, and round the edge, as a fraction of the board side. */
    static final double GAP = 0.02;
    /** Diameter of the round "i" (legend) button, as a fraction of the board side. */
    static final double INFO = 0.05;

    /** An axis-aligned rectangle in pixels (Value Object). */
    public record Rect(double x, double y, double width, double height) {

        public boolean contains(double px, double py) {
            return px >= x && px < x + width && py >= y && py < y + height;
        }

        public boolean overlaps(Rect o) {
            return x < o.x + o.width && o.x < x + width && y < o.y + o.height && o.y < y + height;
        }

        public boolean inside(double w, double h) {
            return x >= 0 && y >= 0 && x + width <= w + 1e-9 && y + height <= h + 1e-9;
        }

        public double centreX() {
            return x + width / 2;
        }

        public double centreY() {
            return y + height / 2;
        }
    }

    private final Rect board;
    private final Map<PlayerColor, Rect> boxes;
    private final Rect info;

    private TableLayout(Rect board, Map<PlayerColor, Rect> boxes, double width, double height) {
        this.board = board;
        this.boxes = boxes;
        this.info = placeInfo(width, height);
    }

    /**
     * The info button goes in the first free spot of: the panel's top-right corner, just below the
     * Yellow box, the bottom-right corner, the top-left corner. Free = inside the panel and over
     * neither the board nor a box.
     */
    private Rect placeInfo(double width, double height) {
        double side = board.width();
        double d = side * INFO;
        double gap = side * GAP;
        Rect yellow = boxes.get(PlayerColor.YELLOW);
        List<Rect> spots = List.of(
                new Rect(width - gap - d, gap, d, d),
                new Rect(yellow.x() + yellow.width() - d, yellow.y() + yellow.height() + gap, d, d),
                new Rect(width - gap - d, height - gap - d, d, d),
                new Rect(gap, gap, d, d));
        for (Rect spot : spots)
            if (isFree(spot, width, height))
                return spot;
        return spots.get(0);
    }

    private boolean isFree(Rect spot, double width, double height) {
        if (!spot.inside(width, height) || spot.overlaps(board))
            return false;
        return boxes.values().stream().noneMatch(spot::overlaps);
    }

    /** Lays out a panel of {@code width} x {@code height} pixels. */
    public static TableLayout of(double width, double height) {
        // Wide: board width + 2 boxes + 2 gaps + 2 margins = width; board + 2 margins = height.
        double wideSide = Math.min(height / (1 + 2 * GAP), width / (1 + 2 * BOX_WIDTH + 4 * GAP));
        // Tall: the same with the boxes above and below.
        double tallSide = Math.min(width / (1 + 2 * GAP), height / (1 + 2 * BOX_HEIGHT + 4 * GAP));
        boolean wide = wideSide >= tallSide;
        double side = Math.max(0, wide ? wideSide : tallSide);
        double bx = (width - side) / 2;
        double by = (height - side) / 2;
        Rect board = new Rect(bx, by, side, side);

        double bw = side * BOX_WIDTH;
        double bh = side * BOX_HEIGHT;
        double gap = side * GAP;
        Map<PlayerColor, Rect> boxes = new EnumMap<>(PlayerColor.class);
        if (wide) {
            double left = bx - gap - bw;
            double right = bx + side + gap;
            boxes.put(PlayerColor.GREEN, new Rect(left, by, bw, bh));
            boxes.put(PlayerColor.YELLOW, new Rect(right, by, bw, bh));
            boxes.put(PlayerColor.RED, new Rect(left, by + side - bh, bw, bh));
            boxes.put(PlayerColor.BLUE, new Rect(right, by + side - bh, bw, bh));
        } else {
            double top = by - gap - bh;
            double bottom = by + side + gap;
            boxes.put(PlayerColor.GREEN, new Rect(bx, top, bw, bh));
            boxes.put(PlayerColor.YELLOW, new Rect(bx + side - bw, top, bw, bh));
            boxes.put(PlayerColor.RED, new Rect(bx, bottom, bw, bh));
            boxes.put(PlayerColor.BLUE, new Rect(bx + side - bw, bottom, bw, bh));
        }
        return new TableLayout(board, boxes, width, height);
    }

    public Rect board() {
        return board;
    }

    /** Size of one of the 15x15 board squares, in pixels. */
    public double cell() {
        return board.width() / 15.0;
    }

    public Rect box(PlayerColor colour) {
        return boxes.get(colour);
    }

    /** The round "i" button that opens the symbol legend (its bounding square). */
    public Rect info() {
        return info;
    }
}
