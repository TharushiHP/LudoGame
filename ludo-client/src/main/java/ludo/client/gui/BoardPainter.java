package ludo.client.gui;

import ludo.client.gui.BoardLayout.GridRect;
import ludo.client.gui.BoardLayout.Spot;
import ludo.client.gui.model.TokenText;
import ludo.shared.BoardConstants;
import ludo.shared.PathMath;
import ludo.shared.PlayerColor;
import ludo.shared.snapshot.MysterySnapshot;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;


final class BoardPainter {

    private final BoardLayout layout;

    BoardPainter(BoardLayout layout) {
        this.layout = layout;
    }

    /** Everything that never changes during a game, with the board's top-left at (x0, y0). */
    void paintBoard(Graphics2D g, double x0, double y0, double cell) {
        double side = cell * BoardLayout.SIZE;
        g.setColor(Color.WHITE);
        g.fill(new Rectangle2D.Double(x0, y0, side, side));
        for (PlayerColor colour : PlayerColor.values()) {
            paintBase(g, x0, y0, cell, colour);
            paintHomeStraight(g, x0, y0, cell, colour);
        }
        for (int c = 0; c < BoardConstants.MAIN_PATH_SIZE; c++)
            paintPathCell(g, x0, y0, cell, c);
        paintHome(g, x0, y0, cell);
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke((float) Math.max(3, cell * 0.12)));
        g.draw(new Rectangle2D.Double(x0, y0, side, side));
    }

    // --- bases ---

    private void paintBase(Graphics2D g, double x0, double y0, double cell, PlayerColor colour) {
        GridRect base = layout.base(colour);
        double bx = x0 + base.col() * cell, by = y0 + base.row() * cell;
        double bs = base.width() * cell;
        g.setColor(Palette.board(colour));
        g.fill(new Rectangle2D.Double(bx, by, bs, bs));
        line(g, cell);
        g.setColor(Color.BLACK);
        g.draw(new Rectangle2D.Double(bx, by, bs, bs));

        Spot centre = layout.baseCentre(colour);
        double cx = x0 + centre.col() * cell, cy = y0 + centre.row() * cell;
        // The big white diamond touches the middle of each side of the base.
        g.setColor(Color.WHITE);
        Path2D outer = diamond(cx, cy, bs / 2);
        g.fill(outer);
        g.setColor(Color.BLACK);
        g.draw(outer);
        // The thinner dashed diamond inside it; its corners meet the ends of the cross arms.
        float dash = (float) Math.max(2, cell * 0.08);
        g.setStroke(new BasicStroke((float) Math.max(1, cell * 0.025), BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER,
                10, new float[]{dash, dash}, 0));
        g.draw(diamond(cx, cy, bs / 2 - cell));
        // The cross: a white centre square and four coloured arms (the token slots).
        line(g, cell);
        double arm = BoardLayout.CROSS_ARM * cell;
        square(g, cx, cy, arm, Color.WHITE);
        for (Spot slot : layout.baseSlots(colour))
            square(g, x0 + slot.col() * cell, y0 + slot.row() * cell, arm, Palette.board(colour));
        // "Base" in the outer corner, as in Figure 1.
        g.setFont(Palette.BOARD_LABEL.deriveFont((float) (cell * 0.62)));
        FontMetrics fm = g.getFontMetrics();
        boolean top = base.row() == 0;
        boolean left = base.col() == 0;
        double pad = cell * 0.18;
        double tx = left ? bx + pad : bx + bs - pad - fm.stringWidth("Base");
        double ty = top ? by + pad + fm.getAscent() * 0.85 : by + bs - pad - fm.getDescent() * 0.4;
        g.setColor(Color.BLACK);
        g.drawString("Base", (float) tx, (float) ty);
    }

    private static Path2D diamond(double cx, double cy, double halfDiagonal) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - halfDiagonal);
        p.lineTo(cx + halfDiagonal, cy);
        p.lineTo(cx, cy + halfDiagonal);
        p.lineTo(cx - halfDiagonal, cy);
        p.closePath();
        return p;
    }

    private static void square(Graphics2D g, double cx, double cy, double size, Color fill) {
        Rectangle2D r = new Rectangle2D.Double(cx - size / 2, cy - size / 2, size, size);
        g.setColor(fill);
        g.fill(r);
        g.setColor(Color.BLACK);
        g.draw(r);
    }

    // --- path ---

    private void paintPathCell(Graphics2D g, double x0, double y0, double cell, int c) {
        GridPos pos = layout.cell(c);
        Rectangle2D r = new Rectangle2D.Double(x0 + pos.col() * cell, y0 + pos.row() * cell, cell, cell);
        PlayerColor startOf = colourWithStart(c);
        if (startOf != null)
            paintStartCell(g, r, cell, startOf);
        else
            paintCell(g, r, cell, Color.WHITE);
        PlayerColor approachOf = colourWithApproach(c);
        if (approachOf != null)
            paintApproachCircle(g, r, cell, approachOf);
        String greek = TokenText.greek(c);
        if (!greek.isEmpty())
            paintGreek(g, r, cell, greek);
    }

    /** One plain square of the path: the fill and the thin grid line round it. */
    static void paintCell(Graphics2D g, Rectangle2D r, double cell, Color fill) {
        g.setColor(fill);
        g.fill(r);
        line(g, cell);
        g.setColor(Palette.GRID_LINE);
        g.draw(r);
    }

    /** A colour's start cell: its board colour with a black "X" (Figure 1). */
    static void paintStartCell(Graphics2D g, Rectangle2D r, double cell, PlayerColor colour) {
        paintCell(g, r, cell, Palette.board(colour));
        g.setColor(Color.BLACK);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.round(cell * 0.7)));
        centred(g, "X", r.getCenterX(), r.getCenterY());
    }

    /** The light circle on a colour's approach cell, where its tokens turn into the home straight. */
    static void paintApproachCircle(Graphics2D g, Rectangle2D r, double cell, PlayerColor colour) {
        double d = cell * 0.52;
        Ellipse2D circle = new Ellipse2D.Double(r.getCenterX() - d / 2, r.getCenterY() - d / 2, d, d);
        g.setColor(Palette.light(colour));
        g.fill(circle);
        g.setColor(Palette.token(colour).darker().darker());
        g.setStroke(new BasicStroke((float) Math.max(1, cell * 0.04)));
        g.draw(circle);
    }

    /** The small α, β or γ in the top-left corner of the Alpha, Beta and Gamma cells. */
    static void paintGreek(Graphics2D g, Rectangle2D r, double cell, String greek) {
        g.setColor(new Color(90, 90, 90));
        g.setFont(new Font(Font.SERIF, Font.BOLD, (int) Math.round(cell * 0.34)));
        g.drawString(greek, (float) (r.getX() + cell * 0.08), (float) (r.getY() + cell * 0.33));
    }

    private void paintHomeStraight(Graphics2D g, double x0, double y0, double cell, PlayerColor colour) {
        for (int i = 0; i < BoardConstants.HOME_STRAIGHT_SIZE; i++) {
            GridPos pos = layout.homeStraight(colour, i);
            Rectangle2D r = new Rectangle2D.Double(x0 + pos.col() * cell, y0 + pos.row() * cell, cell, cell);
            g.setColor(Palette.board(colour));
            g.fill(r);
            line(g, cell);
            g.setColor(Palette.GRID_LINE);
            g.draw(r);
        }
    }

    // --- Home ---

    private void paintHome(Graphics2D g, double x0, double y0, double cell) {
        line(g, cell);
        for (PlayerColor colour : PlayerColor.values()) {
            Path2D triangle = new Path2D.Double();
            boolean first = true;
            for (Spot s : layout.homeTriangle(colour)) {
                double px = x0 + s.col() * cell, py = y0 + s.row() * cell;
                if (first) triangle.moveTo(px, py);
                else triangle.lineTo(px, py);
                first = false;
            }
            triangle.closePath();
            g.setColor(Palette.board(colour));
            g.fill(triangle);
            g.setColor(Color.BLACK);
            g.draw(triangle);
        }
        // "Home" is written in Yellow's (top) triangle and turned with each quarter, so it is upright
        // in Yellow, a quarter turn clockwise in Blue, upside down in Red and anticlockwise in Green.
        g.setFont(Palette.BOARD_LABEL.deriveFont((float) (cell * 0.42)));
        double mid = x0 + 7.5 * cell, midY = y0 + 7.5 * cell;
        for (PlayerColor colour : PlayerColor.values()) {
            AffineTransform saved = g.getTransform();
            g.rotate(Math.PI / 2 * BoardLayout.quarterOf(colour), mid, midY);
            g.setColor(Color.BLACK);
            centred(g, "Home", mid, y0 + 6.26 * cell);
            g.setTransform(saved);
        }
    }

    // --- mystery cell (drawn every frame) ---

    /** A soft pulsing purple glow, a "?" and the rounds left on the mystery cell. */
    void paintMystery(Graphics2D g, double x0, double y0, double cell, MysterySnapshot mystery, long now) {
        if (mystery == null || !mystery.isActive())
            return;
        GridPos pos = layout.cell(mystery.cell());
        paintMysteryAt(g, x0 + (pos.col() + 0.5) * cell, y0 + (pos.row() + 0.5) * cell, cell, mystery.roundsRemaining(), now);
    }

    /** The mystery cell's glow, "?" and {@code rounds} left, centred on (cx, cy). */
    static void paintMysteryAt(Graphics2D g, double cx, double cy, double cell, int rounds, long now) {
        double pulse = 0.5 + 0.5 * Math.sin(now / 300.0);
        double radius = cell * (0.85 + 0.12 * pulse);
        g.setPaint(new RadialGradientPaint(new java.awt.geom.Point2D.Double(cx, cy), (float) radius,
                new float[]{0f, 0.55f, 1f},
                new Color[]{Palette.withAlpha(Palette.MYSTERY, 0.55), Palette.withAlpha(Palette.MYSTERY, 0.25),
                        Palette.withAlpha(Palette.MYSTERY, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Ellipse2D.Double(cx - radius, cy - radius, radius * 2, radius * 2));
        g.setColor(Palette.withAlpha(Palette.MYSTERY, 0.9));
        g.setStroke(new BasicStroke((float) Math.max(1.5, cell * 0.06)));
        g.draw(new RoundRectangle2D.Double(cx - cell * 0.44, cy - cell * 0.44, cell * 0.88, cell * 0.88, cell * 0.2, cell * 0.2));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(cell * 0.55)));
        g.setColor(Palette.MYSTERY.darker());
        centred(g, "?", cx, cy - cell * 0.04);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.round(cell * 0.24)));
        g.drawString(String.valueOf(rounds),(float) (cx + cell * 0.2), (float) (cy + cell * 0.4));
    }

    // --- helpers ---

    private static void line(Graphics2D g, double cell) {
        g.setStroke(new BasicStroke((float) Math.max(1, cell * 0.025)));
    }

    static void centred(Graphics2D g, String text, double cx, double cy) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, (float) (cx - fm.stringWidth(text) / 2.0), (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
    }

    private static PlayerColor colourWithStart(int cell) {
        for (PlayerColor colour : PlayerColor.values())
            if (PathMath.startCell(colour) == cell)
                return colour;
        return null;
    }

    private static PlayerColor colourWithApproach(int cell) {
        for (PlayerColor colour : PlayerColor.values())
            if (PathMath.approachCell(colour) == cell)
                return colour;
        return null;
    }
}
