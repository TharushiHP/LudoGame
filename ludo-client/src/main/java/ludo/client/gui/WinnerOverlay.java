package ludo.client.gui;

import ludo.client.gui.model.Ending;
import ludo.client.gui.model.TableLayout.Rect;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.snapshot.GameStatus;

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.font.GlyphVector;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;

/**
 * The winner screen shown when GAME_OVER arrives; the table dims and {@link Ending} picks the
 * screen. A first-winner ending (Rule 11) shows a large "RED WINS!" in the winner's colour and
 * nothing about the other players. Every other ending shows a podium with places 1-3 (1st in the
 * middle and tallest), 4th place beneath, and how the game ended.
 */
final class WinnerOverlay {

    private WinnerOverlay() {}

    static void paint(Graphics2D g, int width, int height, Rect board, GameOverEvent over, long shownFor) {
        double fade = Math.min(1, shownFor / 500.0);
        Composite saved = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) fade));
        g.setColor(new Color(10, 14, 22, 175));
        g.fillRect(0, 0, width, height);
        Ending ending = Ending.of(over);
        if (ending.isFirstWinner())
            paintFirstWinner(g, board, ending.winner(), shownFor);
        else
            paintPodium(g, board, over);
        g.setComposite(saved);
    }

    /** FIRST_WINNER: a large "RED WINS!" in the winner's colour, scaling in, under the winner's token. */
    private static void paintFirstWinner(Graphics2D g, Rect board, PlayerColor winner, long shownFor) {
        double side = board.width();
        double cx = board.centreX(), cy = board.centreY();
        double t = Math.min(1, shownFor / 450.0);
        double scale = 0.6 + 0.4 * (1 - Math.pow(1 - t, 3)); // ease-out

        double r = side * 0.09 * scale;
        TokenPainter.paint(g, winner, 1, null, cx, cy - side * 0.13, r, 1);

        String text = winner.display().toUpperCase() + " WINS!";
        Font font = new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(24, side * 0.14 * scale));
        GlyphVector glyphs = font.createGlyphVector(g.getFontRenderContext(), text);
        Rectangle2D bounds = glyphs.getVisualBounds();
        Shape outline = glyphs.getOutline((float) (cx - bounds.getCenterX()), (float) (cy + side * 0.1 - bounds.getCenterY()));
        Stroke savedStroke = g.getStroke();
        g.setColor(Color.WHITE);
        g.setStroke(new BasicStroke((float) Math.max(3, side * 0.012), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.draw(outline);
        g.setColor(Palette.token(winner));
        g.fill(outline);
        g.setStroke(savedStroke);

        g.setColor(new Color(220, 225, 235));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.max(12, side * 0.03)));
        BoardPainter.centred(g, "First to bring all four tokens Home", cx, cy + side * 0.24);
    }

    /** Every other ending: places 1-3 on a podium, 4th beneath, and how the game ended. */
    private static void paintPodium(Graphics2D g, Rect board, GameOverEvent over) {
        double side = board.width();
        double cx = board.centreX();
        double top = board.y() + side * 0.1;
        g.setColor(Color.WHITE);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(18, side * 0.075)));
        BoardPainter.centred(g, "Game over", cx, top);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.max(12, side * 0.03)));
        g.setColor(new Color(220, 225, 235));
        BoardPainter.centred(g, ending(over.status()), cx, top + side * 0.07);

        Map<Integer, PlayerColor> byPlace = byPlace(over);
        double stepW = side * 0.22;
        double baseY = board.y() + side * 0.78;
        int[] order = {2, 1, 3};
        double[] heights = {side * 0.2, side * 0.28, side * 0.14};
        for (int i = 0; i < order.length; i++) {
            int place = order[i];
            double x = cx + (i - 1) * (stepW + side * 0.015) - stepW / 2;
            double h = heights[i];
            PlayerColor colour = byPlace.get(place);
            Color fill = colour == null ? new Color(120, 120, 130) : Palette.token(colour);
            g.setPaint(new GradientPaint((float) x, (float) (baseY - h), Palette.mix(fill, Color.WHITE, 0.25),
                    (float) x, (float) baseY, fill.darker()));
            g.fill(new RoundRectangle2D.Double(x, baseY - h, stepW, h, side * 0.02, side * 0.02));
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(16, side * 0.07)));
            BoardPainter.centred(g, String.valueOf(place), x + stepW / 2, baseY - h / 2);
            if (colour != null) {
                double r = side * 0.04;
                TokenPainter.paint(g, colour, place, null, x + stepW / 2, baseY - h - r * 1.3, r, 1);
                g.setColor(Color.WHITE);
                String label = colour.display();
                g.setFont(TablePanel.fit(g, label, new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(11, side * 0.027)), stepW));
                BoardPainter.centred(g, label, x + stepW / 2, baseY - h - r * 3.1);
            }
        }
        PlayerColor fourth = byPlace.get(4);
        g.setColor(new Color(220, 225, 235));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.max(12, side * 0.03)));
        BoardPainter.centred(g, fourth == null ? "" : "4th place: " + fourth.display(),
                cx, baseY + side * 0.06);
        String unplaced = unplaced(over);
        if (!unplaced.isEmpty())
            BoardPainter.centred(g, "No place: " + unplaced, cx, baseY + side * 0.11);
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

    private static String unplaced(GameOverEvent over) {
        StringBuilder text = new StringBuilder();
        for (PlayerColor colour : PlayerColor.values()) {
            Integer place = over.finishPositions().get(colour);
            if (place == null || place == 0)
                text.append(text.length() == 0 ? "" : ", ").append(colour.display());
        }
        return text.toString();
    }

    /** Place (1-4) to colour; a colour with place 0 has none. */
    private static Map<Integer, PlayerColor> byPlace(GameOverEvent over) {
        Map<Integer, PlayerColor> places = new HashMap<>();
        over.finishPositions().forEach((colour, place) -> {
            if (place != null && place > 0)
                places.put(place, colour);
        });
        return places;
    }
}
