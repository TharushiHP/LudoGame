package ludo.client.gui.model;

import ludo.shared.PlayerColor;

import java.util.EnumMap;
import java.util.Map;

/**
 * Where the board and the four player boxes go in a window of a given size (pure geometry, no
 * Swing, unit-tested). The board is the largest square that fits once the player boxes have room
 * outside its corners: beside the board when the window is wide, above and below it when the
 * window is tall. Whichever gives the larger board wins. Each box sits next to its own base:
 * Green top-left, Yellow top-right, Red bottom-left, Blue bottom-right (Figure 1).
 */
public final class TableLayout {

    /** Player box size as a fraction of the board side. */
    static final double BOX_WIDTH = 0.27;
    static final double BOX_HEIGHT = 0.17;
    /** Space between board and boxes, and round the edge, as a fraction of the board side. */
    static final double GAP = 0.02;

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

    private TableLayout(Rect board, Map<PlayerColor, Rect> boxes) {
        this.board = board;
        this.boxes = boxes;
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
        return new TableLayout(board, boxes);
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
}
