package ludo.client.gui;

import ludo.client.gui.model.TableLayout.Rect;
import ludo.client.gui.model.TokenText;
import ludo.shared.BoardConstants;
import ludo.shared.Direction;
import ludo.shared.EffectKind;
import ludo.shared.PieceLocation;
import ludo.shared.PlayerColor;
import ludo.shared.snapshot.PieceSnapshot;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.util.List;

/**
 * The symbol legend opened by the round "i" button: the table dims and a white card lists what
 * each symbol on the board and the tokens means, with the LUDO-T rule it comes from. Every symbol
 * is drawn by the same painter code as the board and the tokens ({@link BoardPainter},
 * {@link TokenPainter}), so the legend can never show a look-alike that differs from the game.
 */
final class LegendOverlay {

    /** One legend row: how to draw its symbol in a square of side {@code s} centred on (x, y), and its text. */
    private record Row(Symbol symbol, String text) {
    }

    @FunctionalInterface
    private interface Symbol {
        void paint(Graphics2D g, double x, double y, double s, long now);
    }

    private static final List<Row> ROWS = List.of(
            new Row((g, x, y, s, now) -> token(g, PlayerColor.RED, x, y, s, true),
                    "Moves clockwise: a coin toss decides when it leaves base (T-1)"),
            new Row((g, x, y, s, now) -> token(g, PlayerColor.GREEN, x, y, s, false),
                    "Moves counterclockwise (T-1)"),
            new Row((g, x, y, s, now) -> {
                double r = s * 0.46;
                TokenPainter.paint(g, PlayerColor.YELLOW, 1, null, x, y, r, 1);
                TokenPainter.captureDot(g, x, y, r);
            }, "Has captured: may enter its home straight (T-7)"),
            new Row(LegendOverlay::block, "Block: own tokens on one cell move together (T-3, T-4)"),
            new Row((g, x, y, s, now) -> TokenPainter.effectBadge(g, EffectKind.ENERGIZED, x, y, s * 0.3),
                    "Energised: moves double (T-12)"),
            new Row((g, x, y, s, now) -> TokenPainter.effectBadge(g, EffectKind.SICK, x, y, s * 0.3),
                    "Sick: moves half (T-12)"),
            new Row((g, x, y, s, now) -> TokenPainter.effectBadge(g, EffectKind.BRIEFING, x, y, s * 0.3),
                    "Briefing: cannot move (T-13)"),
            new Row((g, x, y, s, now) -> {
                BoardPainter.paintCell(g, square(x, y, s), s, Color.WHITE);
                BoardPainter.paintMysteryAt(g, x, y, s, 3, now);
            }, "Mystery cell and the rounds it has left (T-10, T-11)"),
            new Row(LegendOverlay::greekCells, "Alpha / Beta / Gamma: where mystery teleports can land"),
            new Row((g, x, y, s, now) -> BoardPainter.paintStartCell(g, square(x, y, s), s, PlayerColor.RED),
                    "Each colour's start cell"),
            new Row((g, x, y, s, now) -> {
                Rectangle2D r = square(x, y, s);
                BoardPainter.paintCell(g, r, s, Color.WHITE);
                BoardPainter.paintApproachCircle(g, r, s, PlayerColor.RED);
            }, "Approach cell: where a token turns into its home straight"));

    private LegendOverlay() {}

    static void paint(Graphics2D g, int width, int height, Rect board, long now) {
        g.setColor(new Color(10, 14, 22, 150));
        g.fillRect(0, 0, width, height);

        double cardW = Math.min(width - 16, Math.max(board.width() * 0.96, 560));
        double cardH = Math.min(height - 16, board.height() * 0.94);
        double cardX = (width - cardW) / 2, cardY = (height - cardH) / 2;
        double arc = cardH * 0.04;
        g.setColor(Palette.SHADOW);
        g.fill(new RoundRectangle2D.Double(cardX + 3, cardY + 6, cardW, cardH, arc, arc));
        g.setColor(Color.WHITE);
        g.fill(new RoundRectangle2D.Double(cardX, cardY, cardW, cardH, arc, arc));

        double rowH = cardH / (ROWS.size() + 2.6);
        double pad = rowH * 0.5;
        g.setColor(new Color(30, 34, 42));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(14, rowH * 0.6)));
        BoardPainter.centred(g, "What the symbols mean", cardX + cardW / 2, cardY + rowH * 0.85);

        double symbolW = rowH * 3.0;
        double s = rowH * 0.8;
        double textX = cardX + pad + symbolW + pad * 0.6;
        double textW = cardX + cardW - pad - textX;
        // One font for every row: the largest that fits the longest text.
        Font textFont = new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.max(11, rowH * 0.4));
        for (Row row : ROWS)
            textFont = TablePanel.fit(g, row.text(), textFont, textW);
        for (int i = 0; i < ROWS.size(); i++) {
            Row row = ROWS.get(i);
            double cy = cardY + rowH * (1.8 + i) + rowH / 2;
            row.symbol().paint(g, cardX + pad + symbolW / 2, cy, s, now);
            g.setColor(new Color(40, 44, 54));
            g.setFont(textFont);
            FontMetrics fm = g.getFontMetrics();
            g.drawString(row.text(), (float) textX, (float) (cy + (fm.getAscent() - fm.getDescent()) / 2.0));
        }
        g.setColor(new Color(110, 115, 128));
        g.setFont(new Font(Font.SANS_SERIF, Font.ITALIC, (int) Math.max(10, rowH * 0.32)));
        BoardPainter.centred(g, "Click anywhere or press Esc to close", cardX + cardW / 2, cardY + cardH - rowH * 0.45);
    }

    private static void token(Graphics2D g, PlayerColor colour, double x, double y, double s, boolean clockwise) {
        double r = s * 0.46;
        TokenPainter.paint(g, colour, 1, null, x, y, r, 1);
        TokenPainter.directionBadge(g, x, y, r, clockwise);
    }

    /** Two Blue tokens on one square, drawn as the board draws a block (no badges: base tokens have none). */
    private static void block(Graphics2D g, double x, double y, double s, long now) {
        List<PieceSnapshot> pair = List.of(
                new PieceSnapshot(PlayerColor.BLUE, 1, PieceLocation.BASE, -1, Direction.CLOCKWISE, 0, EffectKind.NONE, 0, false),
                new PieceSnapshot(PlayerColor.BLUE, 2, PieceLocation.BASE, -1, Direction.CLOCKWISE, 0, EffectKind.NONE, 0, false));
        TokenPainter.paintStack(g, pair, x, y + s * 0.1, s * 0.42);
    }

    private static void greekCells(Graphics2D g, double x, double y, double s, long now) {
        int[] cells = {BoardConstants.ALPHA, BoardConstants.BETA, BoardConstants.GAMMA};
        for (int i = 0; i < cells.length; i++) {
            Rectangle2D r = square(x + (i - 1) * s, y, s);
            BoardPainter.paintCell(g, r, s, Color.WHITE);
            BoardPainter.paintGreek(g, r, s, TokenText.greek(cells[i]));
        }
    }

    private static Rectangle2D square(double cx, double cy, double s) {
        return new Rectangle2D.Double(cx - s / 2, cy - s / 2, s, s);
    }
}
