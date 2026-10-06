package ludo.client.gui.model;

import ludo.client.gui.model.TableLayout.Rect;

/**
 * Where the winner box and its close button go (pure geometry, no Swing, unit-tested). The box is
 * centred over the board: a short one for a first-winner ending ("Red wins!"), a taller one that
 * lists the places for every other ending. The round close button sits inside its top-right corner.
 *
 * @param box   the box itself
 * @param close the bounding square of the round close button
 */
public record WinnerBox(Rect box, Rect close) {

    /** Box sizes as fractions of the board side. */
    static final double FIRST_WINNER_WIDTH = 0.6;
    static final double FIRST_WINNER_HEIGHT = 0.36;
    static final double PLACES_WIDTH = 0.62;
    static final double PLACES_HEIGHT = 0.64;
    /** Close button diameter and its distance from the box edge, as fractions of the board side. */
    static final double CLOSE = 0.055;
    static final double CLOSE_INSET = 0.022;

    public static WinnerBox of(Rect board, boolean firstWinner) {
        double side = board.width();
        double w = side * (firstWinner ? FIRST_WINNER_WIDTH : PLACES_WIDTH);
        double h = side * (firstWinner ? FIRST_WINNER_HEIGHT : PLACES_HEIGHT);
        Rect box = new Rect(board.centreX() - w / 2, board.centreY() - h / 2, w, h);
        double d = side * CLOSE, inset = side * CLOSE_INSET;
        Rect close = new Rect(box.x() + w - inset - d, box.y() + inset, d, d);
        return new WinnerBox(box, close);
    }

    /** True if (x, y) is on the round close button. */
    public boolean onClose(double x, double y) {
        double r = close.width() / 2;
        return Math.hypot(x - close.centreX(), y - close.centreY()) <= r;
    }
}
