package ludo.client.gui;

import ludo.client.gui.BoardLayout.GridRect;
import ludo.client.gui.BoardLayout.Spot;
import ludo.shared.BoardConstants;
import ludo.shared.PathMath;
import ludo.shared.PlayerColor;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.MysterySnapshot;
import ludo.shared.snapshot.PieceSnapshot;

import javax.swing.JPanel;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Draws the board of Figure 1 and the pieces of the latest snapshot. It only paints what it is
 * given; the layout comes from {@link BoardLayout} and the special cells from {@link BoardConstants}
 * and {@link PathMath}. Used on the Event Dispatch Thread only.
 */
public final class BoardPanel extends JPanel {

    private final BoardLayout layout = new BoardLayout();
    private GameSnapshot snapshot;
    private GridPos hovered;

    public BoardPanel() {
        setPreferredSize(new Dimension(630, 630));
        setBackground(Palette.BOARD_BACKGROUND);
        setToolTipText(""); // turns tooltips on; the text comes from getToolTipText(MouseEvent)
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                GridPos now = gridAt(e.getX(), e.getY());
                if (!java.util.Objects.equals(now, hovered)) {
                    hovered = now;
                    repaint();
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                hovered = null;
                repaint();
            }
        };
        addMouseMotionListener(mouse);
        addMouseListener(mouse);
    }

    public void show(GameSnapshot newSnapshot) {
        this.snapshot = newSnapshot;
        repaint();
    }

    // --- geometry ---

    private double cellSize() {
        return Math.min(getWidth(), getHeight()) / (double) BoardLayout.SIZE;
    }

    private double originX() {
        return (getWidth() - cellSize() * BoardLayout.SIZE) / 2;
    }

    private double originY() {
        return (getHeight() - cellSize() * BoardLayout.SIZE) / 2;
    }

    private int x(double col) {
        return (int) Math.round(originX() + col * cellSize());
    }

    private int y(double row) {
        return (int) Math.round(originY() + row * cellSize());
    }

    private GridPos gridAt(int px, int py) {
        int col = (int) Math.floor((px - originX()) / cellSize());
        int row = (int) Math.floor((py - originY()) / cellSize());
        if (row < 0 || col < 0 || row >= BoardLayout.SIZE || col >= BoardLayout.SIZE)
            return null;
        return new GridPos(row, col);
    }

    // --- painting ---

    @Override
    protected void paintComponent(Graphics graphics) {
        super.paintComponent(graphics);
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        for (PlayerColor colour : PlayerColor.values()) {
            paintBase(g, colour);
            paintHomeStraight(g, colour);
            paintHomeTriangle(g, colour);
        }
        for (int cell = 0; cell < BoardConstants.MAIN_PATH_SIZE; cell++)
            paintPathCell(g, cell);
        if (snapshot != null) {
            paintMystery(g, snapshot.mystery());
            paintPieces(g);
        }
        if (hovered != null) {
            g.setColor(Palette.HOVER);
            g.fillRect(x(hovered.col()), y(hovered.row()), (int) cellSize(), (int) cellSize());
        }
        g.dispose();
    }

    private void paintBase(Graphics2D g, PlayerColor colour) {
        GridRect base = layout.base(colour);
        g.setColor(Palette.of(colour));
        g.fillRect(x(base.col()), y(base.row()), x(base.col() + base.width()) - x(base.col()),
                y(base.row() + base.height()) - y(base.row()));
        g.setColor(Color.WHITE);
        int inset = (int) cellSize();
        g.fillRoundRect(x(base.col()) + inset, y(base.row()) + inset, inset * 4, inset * 4, inset, inset);
        for (Spot slot : layout.baseSlots(colour)) {
            g.setColor(Palette.light(colour));
            fillCircle(g, slot, 0.42);
        }
        g.setColor(Palette.of(colour).darker());
        g.setFont(Palette.BOLD);
        drawCentred(g, colour.display() + " base", x(base.col() + base.width() / 2.0), y(base.row() + 0.55));
    }

    private void paintHomeStraight(Graphics2D g, PlayerColor colour) {
        for (int i = 0; i < BoardConstants.HOME_STRAIGHT_SIZE; i++)
            paintSquare(g, layout.homeStraight(colour, i), Palette.light(colour));
    }

    private void paintHomeTriangle(Graphics2D g, PlayerColor colour) {
        List<Spot> corners = layout.homeTriangle(colour);
        Polygon triangle = new Polygon();
        for (Spot corner : corners)
            triangle.addPoint(x(corner.col()), y(corner.row()));
        g.setColor(Palette.of(colour));
        g.fillPolygon(triangle);
        g.setColor(Palette.GRID_LINE);
        g.drawPolygon(triangle);
    }

    private void paintPathCell(Graphics2D g, int cell) {
        GridPos pos = layout.cell(cell);
        Optional<PlayerColor> startOf = colourWith(cell, true);
        Color fill = startOf.map(Palette::light).orElse(isSpecial(cell) ? Palette.SPECIAL_CELL : Color.WHITE);
        paintSquare(g, pos, fill);
        double size = cellSize();
        startOf.ifPresent(colour -> {
            g.setColor(Palette.of(colour));
            g.setFont(Palette.BOLD.deriveFont((float) (size * 0.55)));
            drawCentred(g, "X", x(pos.col() + 0.5), y(pos.row() + 0.5));
        });
        colourWith(cell, false).ifPresent(colour -> {
            g.setColor(Palette.of(colour));
            g.setStroke(new BasicStroke((float) Math.max(2, size / 12)));
            int d = (int) (size * 0.7);
            g.drawOval(x(pos.col() + 0.15), y(pos.row() + 0.15), d, d);
            g.setStroke(new BasicStroke(1));
        });
        if (isSpecial(cell)) {
            g.setColor(Color.DARK_GRAY);
            g.setFont(Palette.BOLD.deriveFont((float) (size * 0.36)));
            g.drawString(greekLetter(cell), x(pos.col() + 0.08), y(pos.row() + 0.38));
        }
    }

    private void paintMystery(Graphics2D g, MysterySnapshot mystery) {
        if (!mystery.isActive())
            return;
        GridPos pos = layout.cell(mystery.cell());
        double size = cellSize();
        g.setColor(Palette.MYSTERY);
        g.setStroke(new BasicStroke((float) Math.max(3, size / 9)));
        g.drawRect(x(pos.col()) + 2, y(pos.row()) + 2, (int) size - 4, (int) size - 4);
        g.setStroke(new BasicStroke(1));
        g.setFont(Palette.BOLD.deriveFont((float) (size * 0.5)));
        drawCentred(g, "?", x(pos.col() + 0.5), y(pos.row() + 0.45));
        g.setFont(Palette.BOLD.deriveFont((float) (size * 0.26)));
        drawCentred(g, mystery.roundsRemaining() + "r", x(pos.col() + 0.78), y(pos.row() + 0.82));
    }

    private void paintPieces(Graphics2D g) {
        Map<GridPos, List<PieceSnapshot>> byCell = new LinkedHashMap<>();
        for (PieceSnapshot piece : snapshot.pieces()) {
            switch (piece.location()) {
                case BASE -> paintPiece(g, piece, layout.baseSlots(piece.color()).get(piece.number() - 1), 0.36);
                case HOME -> paintPiece(g, piece, layout.homeSlots(piece.color()).get(piece.number() - 1), 0.2);
                case MAIN_PATH -> byCell.computeIfAbsent(layout.cell(piece.position()), k -> new ArrayList<>()).add(piece);
                case HOME_STRAIGHT -> byCell.computeIfAbsent(layout.homeStraight(piece.color(), piece.position()),
                        k -> new ArrayList<>()).add(piece);
            }
        }
        byCell.forEach((pos, pieces) -> {
            if (pieces.size() == 1)
                paintPiece(g, pieces.get(0), new Spot(pos.row() + 0.5, pos.col() + 0.5), 0.4);
            else
                paintBlock(g, pos, pieces);
        });
    }

    /** Two or more pieces on one square: a framed block with up to four small pieces and a count. */
    private void paintBlock(Graphics2D g, GridPos pos, List<PieceSnapshot> pieces) {
        double size = cellSize();
        g.setColor(Color.BLACK);
        g.setStroke(new BasicStroke((float) Math.max(2, size / 14)));
        g.drawRect(x(pos.col()) + 1, y(pos.row()) + 1, (int) size - 2, (int) size - 2);
        g.setStroke(new BasicStroke(1));
        double[][] quarters = {{0.27, 0.27}, {0.27, 0.73}, {0.73, 0.27}, {0.73, 0.73}};
        for (int i = 0; i < Math.min(4, pieces.size()); i++)
            paintPiece(g, pieces.get(i), new Spot(pos.row() + quarters[i][0], pos.col() + quarters[i][1]), 0.22);
        String count = "×" + pieces.size();
        g.setFont(Palette.BOLD.deriveFont((float) (size * 0.3)));
        FontMetrics fm = g.getFontMetrics();
        int bx = x(pos.col() + 1) - fm.stringWidth(count) / 2 - 2;
        int by = y(pos.row()) - fm.getAscent() / 2 + 2;
        g.setColor(Color.BLACK);
        g.fillRoundRect(bx - 3, by - fm.getAscent() + 2, fm.stringWidth(count) + 6, fm.getAscent() + 2, 6, 6);
        g.setColor(Color.WHITE);
        g.drawString(count, bx, by + 1);
    }

    private void paintPiece(Graphics2D g, PieceSnapshot piece, Spot centre, double radius) {
        double size = cellSize();
        g.setColor(Palette.of(piece.color()));
        fillCircle(g, centre, radius);
        g.setColor(Color.BLACK);
        int r = (int) (radius * size);
        g.drawOval(x(centre.col()) - r, y(centre.row()) - r, 2 * r, 2 * r);
        if (radius >= 0.22) {
            g.setColor(Palette.textOn(piece.color()));
            g.setFont(Palette.BOLD.deriveFont((float) Math.max(9, size * radius * 0.8)));
            drawCentred(g, label(piece), x(centre.col()), y(centre.row()));
        }
    }

    // --- helpers ---

    private void paintSquare(Graphics2D g, GridPos pos, Color fill) {
        int x0 = x(pos.col()), y0 = y(pos.row());
        int w = x(pos.col() + 1) - x0, h = y(pos.row() + 1) - y0;
        g.setColor(fill);
        g.fillRect(x0, y0, w, h);
        g.setColor(Palette.GRID_LINE);
        g.drawRect(x0, y0, w, h);
    }

    private void fillCircle(Graphics2D g, Spot centre, double radius) {
        int r = (int) (radius * cellSize());
        g.fillOval(x(centre.col()) - r, y(centre.row()) - r, 2 * r, 2 * r);
    }

    private static void drawCentred(Graphics2D g, String text, int cx, int cy) {
        FontMetrics fm = g.getFontMetrics();
        g.drawString(text, cx - fm.stringWidth(text) / 2, cy + (fm.getAscent() - fm.getDescent()) / 2);
    }

    static String label(PieceSnapshot piece) {
        return piece.color().name().charAt(0) + String.valueOf(piece.number());
    }

    /** The colour whose X cell ({@code start}) or approach cell this is, from the game's own rules. */
    private static Optional<PlayerColor> colourWith(int cell, boolean start) {
        for (PlayerColor colour : PlayerColor.values())
            if ((start ? PathMath.startCell(colour) : PathMath.approachCell(colour)) == cell)
                return Optional.of(colour);
        return Optional.empty();
    }

    private static boolean isSpecial(int cell) {
        return specialName(cell).isPresent();
    }

    static Optional<String> specialName(int cell) {
        if (cell == BoardConstants.ALPHA) return Optional.of("Alpha");
        if (cell == BoardConstants.BETA) return Optional.of("Beta");
        if (cell == BoardConstants.GAMMA) return Optional.of("Gamma");
        return Optional.empty();
    }

    private static String greekLetter(int cell) {
        if (cell == BoardConstants.ALPHA) return "α";
        if (cell == BoardConstants.BETA) return "β";
        return "γ";
    }

    // --- tooltip: what is under the mouse ---

    @Override
    public String getToolTipText(MouseEvent e) {
        GridPos pos = gridAt(e.getX(), e.getY());
        if (pos == null)
            return null;
        StringBuilder text = new StringBuilder();
        Optional<Integer> cell = layout.cellAt(pos);
        if (cell.isPresent()) {
            int c = cell.get();
            text.append("Cell ").append(c);
            colourWith(c, true).ifPresent(col -> text.append(" · ").append(col.display()).append(" start (X)"));
            colourWith(c, false).ifPresent(col -> text.append(" · ").append(col.display()).append(" approach"));
            specialName(c).ifPresent(name -> text.append(" · ").append(name));
            if (snapshot != null && snapshot.mystery().isActive() && snapshot.mystery().cell() == c)
                text.append(" · Mystery cell (").append(snapshot.mystery().roundsRemaining()).append(" rounds left)");
            appendPieces(text, p -> p.isOnMainPath() && p.position() == c);
        } else if (layout.homeStraightAt(pos).isPresent()) {
            PlayerColor colour = layout.homeStraightAt(pos).get();
            int index = homeStraightIndex(colour, pos);
            text.append(colour.display()).append(" home straight, square ").append(index + 1).append(" of 5");
            appendPieces(text, p -> p.color() == colour && p.isOnHomeStraight() && p.position() == index);
        } else if (BoardLayout.CENTRE.contains(pos)) {
            text.append("Home");
            appendPieces(text, PieceSnapshot::isHome);
        } else if (layout.baseAt(pos).isPresent()) {
            PlayerColor colour = layout.baseAt(pos).get();
            text.append(colour.display()).append(" base");
            appendPieces(text, p -> p.color() == colour && p.isAtBase());
        } else {
            return null;
        }
        return text.toString();
    }

    private int homeStraightIndex(PlayerColor colour, GridPos pos) {
        for (int i = 0; i < BoardConstants.HOME_STRAIGHT_SIZE; i++)
            if (layout.homeStraight(colour, i).equals(pos))
                return i;
        return -1;
    }

    private void appendPieces(StringBuilder text, java.util.function.Predicate<PieceSnapshot> where) {
        if (snapshot == null)
            return;
        String pieces = snapshot.pieces().stream().filter(where).map(BoardPanel::label).collect(Collectors.joining(", "));
        if (!pieces.isEmpty())
            text.append(" · ").append(pieces);
    }

    /** A small key under the board for the special cells. */
    public static final class Legend extends JPanel {

        public Legend() {
            setPreferredSize(new Dimension(630, 34));
            setBackground(Palette.BOARD_BACKGROUND);
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setFont(Palette.BASE.deriveFont(13f));
            int x = 8, y = 22, box = 18;
            x = item(g, x, y, box, Palette.light(PlayerColor.RED), "X", Palette.of(PlayerColor.RED), "start (X)");
            g.setColor(Color.WHITE);
            g.fillRect(x, y - 14, box, box);
            g.setColor(Palette.of(PlayerColor.BLUE));
            g.drawOval(x + 3, y - 11, box - 6, box - 6);
            g.setColor(Color.BLACK);
            g.drawString("approach", x + box + 4, y);
            x += box + 4 + g.getFontMetrics().stringWidth("approach") + 16;
            x = item(g, x, y, box, Palette.SPECIAL_CELL, "α", Color.DARK_GRAY, "Alpha/Beta/Gamma (α β γ)");
            x = item(g, x, y, box, Color.WHITE, "?", Palette.MYSTERY, "mystery (rounds left)");
            g.setColor(Color.BLACK);
            g.setStroke(new BasicStroke(2));
            g.drawRect(x, y - 14, box, box);
            g.drawString("block ×N", x + box + 4, y);
            g.dispose();
        }

        private static int item(Graphics2D g, int x, int y, int box, Color fill, String mark, Color markColour, String text) {
            g.setColor(fill);
            g.fillRect(x, y - 14, box, box);
            g.setColor(Palette.GRID_LINE);
            g.drawRect(x, y - 14, box, box);
            g.setColor(markColour);
            Font old = g.getFont();
            g.setFont(old.deriveFont(Font.BOLD));
            g.drawString(mark, x + 4, y);
            g.setFont(old);
            g.setColor(Color.BLACK);
            g.drawString(text, x + box + 4, y);
            return x + box + 4 + g.getFontMetrics().stringWidth(text) + 16;
        }
    }
}
