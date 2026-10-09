package ludo.client.gui;

import ludo.client.gui.model.DiceFaces;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.RoundRectangle2D;

/** Draws a dice with pips (value 0 = a blank dice before the first roll); the pip layout is {@link DiceFaces}. */
final class DicePainter {

    private DicePainter() {}

    
    static void paint(Graphics2D g, double x, double y, double size, int value, double angle) {
        AffineTransform saved = g.getTransform();
        g.rotate(angle, x + size / 2, y + size / 2);
        RoundRectangle2D face = new RoundRectangle2D.Double(x, y, size, size, size * 0.28, size * 0.28);
        g.setColor(Palette.SHADOW);
        g.fill(new RoundRectangle2D.Double(x + size * 0.05, y + size * 0.08, size, size, size * 0.28, size * 0.28));
        g.setPaint(new GradientPaint((float) x, (float) y, Color.WHITE, (float) (x + size), (float) (y + size), new Color(222, 222, 228)));
        g.fill(face);
        g.setColor(new Color(60, 60, 60));
        g.setStroke(new BasicStroke((float) Math.max(1, size * 0.04)));
        g.draw(face);
        if (value >= 1 && value <= 6) {
            double pr = size * 0.09;
            g.setColor(value == 1 ? new Color(200, 20, 30) : new Color(25, 25, 25));
            for (DiceFaces.Pip pip : DiceFaces.pips(value))
                g.fill(new Ellipse2D.Double(x + pip.x() * size - pr, y + pip.y() * size - pr, pr * 2, pr * 2));
        }
        g.setTransform(saved);
    }
}
