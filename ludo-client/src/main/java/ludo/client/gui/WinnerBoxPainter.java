package ludo.client.gui;

import ludo.client.gui.model.Banner;
import ludo.client.gui.model.Ending;
import ludo.client.gui.model.TableLayout.Rect;
import ludo.client.gui.model.WinnerBox;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.snapshot.GameStatus;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Line2D;
import java.awt.geom.RoundRectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Draws the winner box shown when GAME_OVER arrives, in the style of the event banners
 * ({@link Overlays}): a rounded box over the board with a drop shadow and the same short scale-in,
 * but larger, and it stays until it is closed or the next game starts (no dimming, no fade-out).
 * <ul>
 *   <li>First-winner ending (Rule 11): the winner's token and "Red wins!" in the winner's colour,
 *       nothing about the other players.</li>
 *   <li>Every other ending: the places, each with a small token, and how the game ended.</li>
 * </ul>
 * If the server announced a next game, a countdown line says when it starts. The round "×" in the
 * top-right corner closes the box; where it is comes from {@link WinnerBox}.
 */
final class WinnerBoxPainter {

    private static final Color TEXT = new Color(40, 44, 54);
    private static final Color MUTED = new Color(105, 110, 122);

    private WinnerBoxPainter() {}

    /**
     * @param shownFor     ms since the box appeared (for the scale-in)
     * @param nextGameInMs ms until the next game starts, or a negative number when none follows
     * @param closeHover   the mouse is over the close button
     */
    static void paint(Graphics2D g, Rect board, GameOverEvent over, long shownFor, long nextGameInMs, boolean closeHover) {
        Ending ending = Ending.of(over);
        WinnerBox geometry = WinnerBox.of(board, ending.isFirstWinner());
        Rect box = geometry.box();
        double side = board.width();
        double t = shownFor;
        double alpha = Math.min(1, t / 120.0);
        double scale = t < 160 ? 0.85 + 0.15 * (t / 160.0) : 1; // the banners' pop-in

        Composite savedComposite = g.getComposite();
        AffineTransform saved = g.getTransform();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) alpha));
        g.translate(box.centreX(), box.centreY());
        g.scale(scale, scale);
        g.translate(-box.centreX(), -box.centreY());

        double arc = side * 0.08;
        Color accent = accent(ending, over);
        g.setColor(Palette.SHADOW);
        g.fill(new RoundRectangle2D.Double(box.x() + 3, box.y() + 5, box.width(), box.height(), arc, arc));
        RoundRectangle2D shape = new RoundRectangle2D.Double(box.x(), box.y(), box.width(), box.height(), arc, arc);
        g.setColor(Color.WHITE);
        g.fill(shape);
        g.setColor(accent);
        g.setStroke(new BasicStroke((float) Math.max(3, side * 0.012)));
        g.draw(shape);

        if (ending.isFirstWinner())
            paintFirstWinner(g, box, side, ending.winner());
        else
            paintPlaces(g, box, side, over);
        if (nextGameInMs >= 0) {
            long seconds = (nextGameInMs + 999) / 1000;
            String text = seconds > 0 ? "Next game starts in " + seconds + " s" : "Next game starting...";
            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.max(12, side * 0.03)));
            BoardPainter.centred(g, text, box.centreX(), box.y() + box.height() - side * 0.04);
        }
        paintClose(g, geometry.close(), closeHover);

        g.setTransform(saved);
        g.setComposite(savedComposite);
    }

    private static void paintFirstWinner(Graphics2D g, Rect box, double side, PlayerColor winner) {
        double r = side * 0.055;
        TokenPainter.paint(g, winner, 1, null, box.centreX(), box.y() + side * 0.1, r, 1);
        g.setColor(Palette.token(winner).darker());
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(20, side * 0.085)));
        BoardPainter.centred(g, winner.display() + " wins!", box.centreX(), box.y() + side * 0.23);
    }

    private static void paintPlaces(Graphics2D g, Rect box, double side, GameOverEvent over) {
        g.setColor(TEXT);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(16, side * 0.055)));
        BoardPainter.centred(g, "Game over", box.centreX(), box.y() + side * 0.07);
        g.setColor(MUTED);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.max(12, side * 0.03)));
        BoardPainter.centred(g, ending(over.status()), box.centreX(), box.y() + side * 0.13);

        Map<Integer, PlayerColor> byPlace = new TreeMap<>();
        List<String> unplaced = new ArrayList<>();
        for (PlayerColor colour : PlayerColor.values()) {
            int place = over.finishPositions().getOrDefault(colour, 0);
            if (place > 0)
                byPlace.put(place, colour);
            else
                unplaced.add(colour.display());
        }
        double rowH = side * 0.075;
        double y = box.y() + side * 0.21;
        double tokenX = box.x() + box.width() * 0.22;
        Font rowFont = new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(13, side * 0.04));
        for (Map.Entry<Integer, PlayerColor> entry : byPlace.entrySet()) {
            PlayerColor colour = entry.getValue();
            TokenPainter.paint(g, colour, entry.getKey(), null, tokenX, y, rowH * 0.36, 1);
            g.setFont(rowFont);
            g.setColor(Palette.token(colour).darker());
            FontMetrics fm = g.getFontMetrics();
            g.drawString(Banner.ordinal(entry.getKey()) + "  " + colour.display(), (float) (tokenX + rowH * 0.7),
                    (float) (y + (fm.getAscent() - fm.getDescent()) / 2.0));
            y += rowH;
        }
        if (!unplaced.isEmpty()) {
            g.setColor(MUTED);
            g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.max(12, side * 0.03)));
            BoardPainter.centred(g, "No place: " + String.join(", ", unplaced), box.centreX(), y);
        }
    }

    /** The round "×" button: light grey, darker under the mouse. */
    private static void paintClose(Graphics2D g, Rect close, boolean hover) {
        double r = close.width() / 2, cx = close.centreX(), cy = close.centreY();
        g.setColor(hover ? new Color(205, 210, 220) : new Color(236, 238, 242));
        g.fill(TokenPainter.circle(cx, cy, r));
        g.setColor(hover ? TEXT : MUTED);
        g.setStroke(new BasicStroke((float) Math.max(2, r * 0.18), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        double a = r * 0.42;
        g.draw(new Line2D.Double(cx - a, cy - a, cx + a, cy + a));
        g.draw(new Line2D.Double(cx - a, cy + a, cx + a, cy - a));
    }

    /** The border colour: the winner's (or 1st place's) colour, grey when nobody is placed. */
    private static Color accent(Ending ending, GameOverEvent over) {
        if (ending.isFirstWinner())
            return Palette.token(ending.winner());
        for (PlayerColor colour : PlayerColor.values())
            if (over.finishPositions().getOrDefault(colour, 0) == 1)
                return Palette.token(colour);
        return new Color(120, 126, 138);
    }

    static String ending(GameStatus status) {
        return switch (status) {
            case FINISHED -> "Every place is decided"; // the last player takes 4th without reaching Home
            case STALEMATE -> "Ended in a stalemate: no player could move any more";
            case ROUND_CAP_REACHED -> "Ended at the round limit";
            case ABORTED -> "The game was stopped by the server";
            default -> status.name();
        };
    }
}
