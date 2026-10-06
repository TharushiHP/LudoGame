package ludo.client.gui;

import ludo.shared.Direction;
import ludo.shared.EffectKind;
import ludo.shared.PlayerColor;
import ludo.shared.snapshot.PieceSnapshot;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * Draws tokens as glossy game pieces (shadow, white outer ring, darker rim, shaded body, white
 * highlight, piece number) and their LUDO-T badges, the way real Ludo apps show state on the
 * pieces themselves:
 * <ul>
 *   <li>top-right: the direction the piece travels (↻ clockwise, ↺ counterclockwise; T-1);</li>
 *   <li>top-left: a gold dot once the piece has captured (T-7: only then may it enter its home straight);</li>
 *   <li>bottom-right: its effect: a lightning bolt (energised), "½" (sick) or a pause sign (briefing).</li>
 * </ul>
 * A block (two or more tokens on one square) is drawn as stacked tokens with a "×N" count.
 */
final class TokenPainter {

    private TokenPainter() {}

    /** One token centred on (cx, cy) with radius r. {@code piece} null = no badges. */
    static void paint(Graphics2D g, PlayerColor colour, int number, PieceSnapshot piece,
                      double cx, double cy, double r, double alpha) {
        Composite saved = g.getComposite();
        if (alpha < 1)
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, alpha)));
        // Shadow, then a white ring so the token stands out on its own colour.
        g.setColor(Palette.SHADOW);
        g.fill(circle(cx + r * 0.08, cy + r * 0.16, r * 1.02));
        g.setColor(Color.WHITE);
        g.fill(circle(cx, cy, r));
        Color body = Palette.token(colour);
        g.setColor(body.darker().darker());
        g.fill(circle(cx, cy, r * 0.88));
        // Shaded body: light at the top-left, the colour, darker at the edge.
        double br = r * 0.78;
        g.setPaint(new RadialGradientPaint(new Point2D.Double(cx - br * 0.35, cy - br * 0.4), (float) (br * 1.6),
                new float[]{0f, 0.45f, 1f},
                new Color[]{Palette.mix(body, Color.WHITE, 0.45), body, body.darker()},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(circle(cx, cy, br));
        // White highlight.
        g.setColor(new Color(255, 255, 255, 120));
        g.fill(new Ellipse2D.Double(cx - br * 0.6, cy - br * 0.78, br * 0.95, br * 0.6));
        // Number.
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(8, Math.round(r * 0.95))));
        g.setColor(colour == PlayerColor.YELLOW ? new Color(60, 40, 0) : Color.WHITE);
        BoardPainter.centred(g, String.valueOf(number), cx, cy + r * 0.04);
        if (piece != null && piece.isActive())
            paintBadges(g, piece, cx, cy, r);
        g.setComposite(saved);
    }

    /** Two or more tokens on one square: a small stack, the top one with badges, and "×N". */
    static void paintStack(Graphics2D g, List<PieceSnapshot> pieces, double cx, double cy, double r) {
        int shown = Math.min(3, pieces.size());
        double lift = r * 0.3;
        for (int i = 0; i < shown; i++) {
            PieceSnapshot p = pieces.get(pieces.size() - shown + i);
            double y = cy + lift * (shown - 1) / 2.0 - lift * i;
            paint(g, p.color(), p.number(), i == shown - 1 ? p : null, cx, y, r, 1);
        }
        String count = "×" + pieces.size();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(8, Math.round(r * 0.62))));
        double w = g.getFontMetrics().stringWidth(count) + r * 0.4, h = r * 0.72;
        double x = cx + r * 0.35, y = cy - r * 1.35 - lift * (shown - 1) / 2.0;
        g.setColor(Color.BLACK);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        g.setColor(Color.WHITE);
        BoardPainter.centred(g, count, x + w / 2, y + h / 2);
    }

    private static void paintBadges(Graphics2D g, PieceSnapshot piece, double cx, double cy, double r) {
        double b = r * 0.36; // badge radius
        // Direction, top-right.
        double dx = cx + r * 0.72, dy = cy - r * 0.72;
        badge(g, dx, dy, b, Color.WHITE);
        g.setColor(new Color(30, 30, 30));
        g.setStroke(new BasicStroke((float) Math.max(1, b * 0.28), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double a = b * 0.55;
        boolean cw = piece.direction() == Direction.CLOCKWISE;
        g.draw(new Arc2D.Double(dx - a, dy - a, a * 2, a * 2, cw ? 120 : 60, cw ? -270 : 270, Arc2D.OPEN));
        // Arrowhead at the end of the arc (angle 210° for clockwise, -30° for counterclockwise).
        double end = Math.toRadians(cw ? 210 : -30);
        double ex = dx + a * Math.cos(end), ey = dy - a * Math.sin(end);
        double tangent = end + (cw ? -Math.PI / 2 : Math.PI / 2);
        double tx = Math.cos(tangent), ty = -Math.sin(tangent);
        double h = b * 0.45;
        Path2D head = new Path2D.Double();
        head.moveTo(ex + tx * h, ey + ty * h);
        head.lineTo(ex - ty * h * 0.8, ey + tx * h * 0.8);
        head.lineTo(ex + ty * h * 0.8, ey - tx * h * 0.8);
        head.closePath();
        g.fill(head);
        // Captured at least once, top-left.
        if (piece.hasCapture()) {
            double gx = cx - r * 0.78, gy = cy - r * 0.78, gr = b * 0.62;
            g.setColor(new Color(255, 200, 0));
            g.fill(circle(gx, gy, gr));
            g.setColor(new Color(120, 80, 0));
            g.setStroke(new BasicStroke((float) Math.max(1, gr * 0.3)));
            g.draw(circle(gx, gy, gr));
        }
        // Effect, bottom-right.
        if (piece.effect() != EffectKind.NONE)
            paintEffect(g, piece.effect(), cx + r * 0.72, cy + r * 0.72, b);
    }

    private static void paintEffect(Graphics2D g, EffectKind effect, double x, double y, double b) {
        switch (effect) {
            case ENERGIZED -> {
                badge(g, x, y, b, new Color(255, 140, 0));
                Path2D bolt = new Path2D.Double();
                bolt.moveTo(x + b * 0.15, y - b * 0.75);
                bolt.lineTo(x - b * 0.4, y + b * 0.1);
                bolt.lineTo(x - b * 0.02, y + b * 0.1);
                bolt.lineTo(x - b * 0.18, y + b * 0.75);
                bolt.lineTo(x + b * 0.42, y - b * 0.12);
                bolt.lineTo(x + b * 0.04, y - b * 0.12);
                bolt.closePath();
                g.setColor(new Color(255, 245, 120));
                g.fill(bolt);
            }
            case SICK -> {
                badge(g, x, y, b, new Color(110, 150, 40));
                g.setColor(Color.WHITE);
                g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(7, Math.round(b * 1.2))));
                BoardPainter.centred(g, "½", x, y);
            }
            case BRIEFING -> {
                badge(g, x, y, b, new Color(70, 80, 100));
                g.setColor(Color.WHITE);
                double w = b * 0.26, hgt = b * 0.95;
                g.fill(new java.awt.geom.Rectangle2D.Double(x - w * 1.5, y - hgt / 2, w, hgt));
                g.fill(new java.awt.geom.Rectangle2D.Double(x + w * 0.5, y - hgt / 2, w, hgt));
            }
            default -> { }
        }
    }

    private static void badge(Graphics2D g, double x, double y, double b, Color fill) {
        g.setColor(new Color(30, 30, 30));
        g.fill(circle(x, y, b * 1.12));
        g.setColor(fill);
        g.fill(circle(x, y, b));
    }

    static Ellipse2D circle(double cx, double cy, double r) {
        return new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2);
    }
}
