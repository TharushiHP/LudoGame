package ludo.client.gui;

import ludo.client.gui.model.TableLayout.Rect;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.snapshot.GameStatus;

import java.awt.AlphaComposite;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.geom.RoundRectangle2D;
import java.util.HashMap;
import java.util.Map;

/**
 * The winner screen shown when GAME_OVER arrives: the table dims and a podium shows places 1-3
 * (1st in the middle and tallest), 4th place beneath, and how the game ended.
 */
final class WinnerOverlay {

    private WinnerOverlay() {}

    static void paint(Graphics2D g, int width, int height, Rect board, GameOverEvent over, long shownFor) {
        double fade = Math.min(1, shownFor / 500.0);
        Composite saved = g.getComposite();
        g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, (float) fade));
        g.setColor(new Color(10, 14, 22, 175));
        g.fillRect(0, 0, width, height);

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
                String label = colour.display() + " · " + Palette.strategy(colour);
                g.setFont(TablePanel.fit(g, label, new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(11, side * 0.027)), stepW));
                BoardPainter.centred(g, label, x + stepW / 2, baseY - h - r * 3.1);
            }
        }
        PlayerColor fourth = byPlace.get(4);
        g.setColor(new Color(220, 225, 235));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.max(12, side * 0.03)));
        BoardPainter.centred(g, fourth == null ? "" : "4th place: " + fourth.display() + " · " + Palette.strategy(fourth),
                cx, baseY + side * 0.06);
        String unplaced = unplaced(over);
        if (!unplaced.isEmpty())
            BoardPainter.centred(g, "No place: " + unplaced, cx, baseY + side * 0.11);
        g.setComposite(saved);
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
