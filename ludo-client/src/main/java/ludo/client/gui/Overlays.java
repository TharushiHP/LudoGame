package ludo.client.gui;

import ludo.client.gui.model.Banner;
import ludo.client.gui.model.TableLayout.Rect;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The short messages drawn over the board, like a real online game shows them:
 * <ul>
 *   <li><b>banners</b> for important moments (capture, mystery teleport, effect, block, Home,
 *       a player finishing), one at a time in the middle of the board, fading after about 1.5 s;</li>
 *   <li><b>toasts</b> for problems only ("Reconnecting...", "Waiting for Blue...", "Blue is now
 *       played by the computer"), at the bottom of the board. A sticky toast stays while its
 *       condition lasts; a normal one goes after 3 s.</li>
 * </ul>
 * EDT only. Times are passed in; the window's Swing timer repaints.
 */
final class Overlays {

    static final long BANNER_MS = 1500;
    static final long BANNER_SHORT_MS = 900;
    static final int MAX_QUEUED = 4;
    static final long TOAST_MS = 3000;

    private final Deque<Banner> queue = new ArrayDeque<>();
    private Banner current;
    private long currentStart;
    private long currentDuration;

    private final Map<String, String> sticky = new LinkedHashMap<>();
    private final List<Toast> toasts = new ArrayList<>();

    private record Toast(String text, long until) {
    }

    // --- banners ---

    void banners(List<Banner> banners) {
        queue.addAll(banners);
        while (queue.size() > MAX_QUEUED)
            queue.removeFirst(); // old news: the newest moments matter more
    }

    // --- toasts ---

    /** A toast that stays until {@link #clear(String)} with the same key. */
    void sticky(String key, String text) {
        sticky.put(key, text);
    }

    void clear(String key) {
        sticky.remove(key);
    }

    void toast(String text, long now) {
        toasts.add(new Toast(text, now + TOAST_MS));
    }

    boolean busy() {
        return current != null || !queue.isEmpty() || !toasts.isEmpty();
    }

    // --- painting ---

    void paint(Graphics2D g, Rect board, long now) {
        paintBanner(g, board, now);
        paintToasts(g, board, now);
    }

    private void paintBanner(Graphics2D g, Rect board, long now) {
        if (current != null && now >= currentStart + currentDuration)
            current = null;
        if (current == null && !queue.isEmpty()) {
            current = queue.removeFirst();
            currentStart = now;
            currentDuration = queue.isEmpty() ? BANNER_MS : BANNER_SHORT_MS;
        }
        if (current == null)
            return;
        double t = now - currentStart;
        double alpha = Math.min(1, t / 120.0) * Math.min(1, (currentDuration - t) / 400.0);
        double scale = t < 160 ? 0.85 + 0.15 * (t / 160.0) : 1;
        double side = board.width();
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(13, side * 0.036));
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        double w = Math.min(side * 0.9, fm.stringWidth(current.text()) + side * 0.08);
        double h = fm.getHeight() + side * 0.035;
        double cx = board.centreX(), cy = board.y() + side * 0.5;
        Composite savedComposite = g.getComposite();
        AffineTransform saved = g.getTransform();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) Math.max(0, alpha)));
        g.translate(cx, cy);
        g.scale(scale, scale);
        RoundRectangle2D pill = new RoundRectangle2D.Double(-w / 2, -h / 2, w, h, h, h);
        g.setColor(Palette.SHADOW);
        g.fill(new RoundRectangle2D.Double(-w / 2 + 3, -h / 2 + 5, w, h, h, h));
        Color colour = Palette.token(current.colour());
        g.setColor(colour);
        g.fill(pill);
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) Math.max(2, side * 0.006)));
        g.draw(pill);
        g.setColor(current.colour() == ludo.shared.PlayerColor.YELLOW ? Color.BLACK : Color.WHITE);
        BoardPainter.centred(g, current.text(), 0, 0);
        g.setTransform(saved);
        g.setComposite(savedComposite);
    }

    private void paintToasts(Graphics2D g, Rect board, long now) {
        toasts.removeIf(t -> now >= t.until());
        List<String> lines = new ArrayList<>(sticky.values());
        for (Iterator<Toast> it = toasts.iterator(); it.hasNext(); )
            lines.add(it.next().text());
        if (lines.isEmpty())
            return;
        double side = board.width();
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(12, side * 0.026)));
        FontMetrics fm = g.getFontMetrics();
        double h = fm.getHeight() + side * 0.02;
        double y = board.y() + side * 0.93 - h;
        for (int i = lines.size() - 1; i >= 0; i--) {
            String text = lines.get(i);
            double w = fm.stringWidth(text) + side * 0.05;
            RoundRectangle2D pill = new RoundRectangle2D.Double(board.centreX() - w / 2, y, w, h, h, h);
            g.setColor(Palette.TOAST);
            g.fill(pill);
            g.setColor(Color.WHITE);
            BoardPainter.centred(g, text, board.centreX(), y + h / 2);
            y -= h + side * 0.012;
        }
    }
}
