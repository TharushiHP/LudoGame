package ludo.client.gui;

import ludo.client.control.Identity;
import ludo.client.gui.BoardLayout.Spot;
import ludo.client.gui.model.Banner;
import ludo.client.gui.model.DiceFaces;
import ludo.client.gui.model.Ending;
import ludo.client.gui.model.PieceChange;
import ludo.client.gui.model.Place;
import ludo.client.gui.model.TableLayout;
import ludo.client.gui.model.TableLayout.Rect;
import ludo.client.gui.model.TokenText;
import ludo.client.gui.model.WinnerBox;
import ludo.shared.BoardConstants;
import ludo.shared.Direction;
import ludo.shared.EffectKind;
import ludo.shared.PieceLocation;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.StateEvent;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;
import ludo.shared.snapshot.PieceSnapshot;

import javax.swing.JComponent;
import javax.swing.ToolTipManager;
import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Composite;
import java.awt.Cursor;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Rectangle2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * The game table: the Figure 1 board as large as the window allows, a player box with a dice
 * outside each corner, the tokens, and the banners, toasts and winner box on top, plus a round
 * "i" button that opens the symbol legend ({@link LegendOverlay}). The winner box
 * ({@link WinnerBoxPainter}) stays until its "×" or Esc closes it or the next game starts.
 * Everything is drawn with Java2D and scales with the window ({@link TableLayout}).
 * <p>
 * {@link #apply} sets the new snapshot at once (that is what the controller waits for before it
 * ACKs); in the spectator window the {@link Animator} then shows how the tokens got there.
 * EDT only.
 */
final class TablePanel extends JComponent {

    static final long TUMBLE_MS = 220;
    static final int TUMBLE_FRAMES = 6;

    private final Identity me;
    private final boolean animate;
    private final BoardLayout layout = new BoardLayout();
    private final BoardPainter boardPainter = new BoardPainter(layout);
    private final Animator animator = new Animator();
    private final Overlays overlays = new Overlays();

    private GameSnapshot snapshot;
    private PlayerColor requested;      // whose ROLL/DECISION the server waits for
    private PlayerColor lastRoller;     // who rolled in the latest STATE
    private final Map<PlayerColor, Integer> dice = new EnumMap<>(PlayerColor.class);
    private int[] tumbleFaces;
    private long tumbleStart;
    private final Set<PlayerColor> substituted = EnumSet.noneOf(PlayerColor.class);
    private GameOverEvent gameOver;
    private long gameOverAt;
    private boolean winnerBoxOpen;
    private boolean closeHover;
    private long nextGameAt;            // when the announced next game starts (ms), 0 = none

    private BufferedImage boardImage;
    private double boardImageKey;
    private TableLayout table;
    private final List<Hit> hits = new ArrayList<>();
    private boolean legendOpen;
    private boolean infoHover;

    /** A token on screen and its hover text. */
    private record Hit(Ellipse2D area, String text) {
    }

    TablePanel(Identity me, boolean animate) {
        this.me = me;
        this.animate = animate;
        setOpaque(true);
        ToolTipManager.sharedInstance().registerComponent(this);
        ToolTipManager.sharedInstance().setInitialDelay(250);
        overlays.sticky("players", "Waiting for players...");
        MouseAdapter mouse = new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                clicked(e.getX(), e.getY());
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                boolean overInfo = onInfo(e.getX(), e.getY());
                boolean overClose = onWinnerClose(e.getX(), e.getY());
                if (overInfo != infoHover || overClose != closeHover) {
                    infoHover = overInfo;
                    closeHover = overClose;
                    setCursor(Cursor.getPredefinedCursor(overInfo || overClose ? Cursor.HAND_CURSOR : Cursor.DEFAULT_CURSOR));
                    repaint();
                }
            }
        };
        addMouseListener(mouse);
        addMouseMotionListener(mouse);
    }

    // --- the "i" button, the symbol legend and the winner box's close button (EDT) ---

    /**
     * A click on the "i" button opens the legend; any click while it is open closes it. Otherwise a
     * click on the winner box's "×" closes the box. Purely local: nothing is sent to the server.
     */
    private void clicked(double x, double y) {
        if (legendOpen)
            closeLegend();
        else if (onInfo(x, y)) {
            legendOpen = true;
            repaint();
        } else if (onWinnerClose(x, y)) {
            closeWinnerBox();
        }
    }

    /** Esc: closes the legend if it is open, otherwise the winner box. */
    void escape() {
        if (legendOpen)
            closeLegend();
        else
            closeWinnerBox();
    }

    private boolean winnerBoxShown() {
        return gameOver != null && winnerBoxOpen && table != null;
    }

    private boolean onWinnerClose(double x, double y) {
        return winnerBoxShown() && winnerBox().onClose(x, y);
    }

    private WinnerBox winnerBox() {
        return WinnerBox.of(table.board(), Ending.of(gameOver).isFirstWinner());
    }

    /** Hides the winner box; the window stays read-only (a spectator never sends anything). */
    void closeWinnerBox() {
        if (winnerBoxOpen) {
            winnerBoxOpen = false;
            closeHover = false;
            setCursor(Cursor.getDefaultCursor());
            repaint();
        }
    }

    boolean winnerBoxOpen() {
        return gameOver != null && winnerBoxOpen;
    }

    private boolean onInfo(double x, double y) {
        if (table == null)
            return false;
        Rect info = table.info();
        double r = info.width() / 2;
        return Math.hypot(x - info.centreX(), y - info.centreY()) <= r;
    }

    /** Closes the legend (a click anywhere, or Esc through the window's key binding). */
    void closeLegend() {
        if (legendOpen) {
            legendOpen = false;
            repaint();
        }
    }

    boolean legendOpen() {
        return legendOpen;
    }

    Overlays overlays() {
        return overlays;
    }

    // --- updates (EDT) ---

    /** Applies a STATE at once; the spectator window then animates towards it. */
    void apply(StateEvent state) {
        long now = System.currentTimeMillis();
        GameSnapshot previous = snapshot;
        snapshot = state.snapshot();
        overlays.clear("players");
        animator.snap();
        Map<PlayerColor, Integer> rolls = DiceFaces.rollsIn(state.log());
        dice.putAll(rolls);
        lastRoller = rolls.isEmpty() ? null : new ArrayList<>(rolls.keySet()).get(rolls.size() - 1);
        boolean tumble = animate && lastRoller != null;
        tumbleFaces = tumble ? DiceFaces.tumble(rolls.get(lastRoller), TUMBLE_FRAMES, state.version()) : null;
        tumbleStart = now;
        if (animate && previous != null)
            animator.start(PieceChange.between(previous, snapshot), now, tumble ? TUMBLE_MS : 0);
        overlays.banners(Banner.fromLog(state.log(), snapshot));
        if (snapshot.status() != GameStatus.IN_PROGRESS)
            requested = null;
        repaint();
    }

    void request(PlayerColor colour) {
        requested = colour;
    }

    void substituted(PlayerColor colour) {
        substituted.add(colour);
    }

    /** Opens the winner box; if the server announced a next game, the box counts down to it. */
    void gameOver(GameOverEvent over) {
        gameOver = over;
        gameOverAt = System.currentTimeMillis();
        winnerBoxOpen = true;
        nextGameAt = over.hasNextGame() ? gameOverAt + over.nextGameInMs() : 0;
        requested = null;
        overlays.clear("players");
    }

    /**
     * NEW_GAME: the server starts the next game with the same seats. The winner box closes, old
     * banners go, and every token is shown in base until the new game's first STATE arrives.
     * Substituted colours stay marked: the server keeps playing them.
     */
    void newGame() {
        gameOver = null;
        winnerBoxOpen = false;
        closeHover = false;
        nextGameAt = 0;
        snapshot = null;
        requested = null;
        lastRoller = null;
        tumbleFaces = null;
        dice.clear();
        animator.snap();
        overlays.clearBanners();
        setCursor(Cursor.getDefaultCursor());
        repaint();
    }

    /** For tests: the GAME_OVER being shown, null during a game. */
    GameOverEvent gameOver() {
        return gameOver;
    }

    /** The player whose box glows: the one still animating its move, else the one the server waits for. */
    private PlayerColor active(long now) {
        if (gameOver != null || (snapshot != null && snapshot.status() != GameStatus.IN_PROGRESS))
            return null;
        if (lastRoller != null && (animator.busy(now) || tumbling(now)))
            return lastRoller;
        if (requested != null)
            return requested;
        return snapshot == null ? null : snapshot.currentPlayer();
    }

    private boolean tumbling(long now) {
        return tumbleFaces != null && now - tumbleStart < TUMBLE_MS;
    }

    // --- painting ---

    @Override
    protected void paintComponent(Graphics graphics) {
        long now = System.currentTimeMillis();
        Graphics2D g = (Graphics2D) graphics.create();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        int w = getWidth(), h = getHeight();
        g.setPaint(new GradientPaint(0, 0, Palette.TABLE, 0, h, Palette.TABLE_EDGE));
        g.fillRect(0, 0, w, h);

        table = TableLayout.of(w, h);
        Rect board = table.board();
        double cell = table.cell();
        if (cell <= 1) {
            g.dispose();
            return;
        }
        g.setColor(Palette.SHADOW);
        g.fill(new Rectangle2D.Double(board.x() + cell * 0.12, board.y() + cell * 0.2, board.width(), board.height()));
        paintBoardImage(g, board, cell);
        if (snapshot != null)
            boardPainter.paintMystery(g, board.x(), board.y(), cell, snapshot.mystery(), now);
        paintTokens(g, board, cell, now);
        PlayerColor active = active(now);
        for (PlayerColor colour : PlayerColor.values())
            paintBox(g, colour, table.box(colour), colour == active, now);
        overlays.paint(g, board, now);
        if (winnerBoxShown() && !animator.busy(now))
            WinnerBoxPainter.paint(g, board, gameOver, now - gameOverAt,
                    nextGameAt == 0 ? -1 : Math.max(0, nextGameAt - now), closeHover);
        if (legendOpen)
            LegendOverlay.paint(g, w, h, board, now);
        paintInfoButton(g, table.info());
        g.dispose();
    }

    /** The round "i" button: a white disc with a dark rim and a bold "i", lighter under the mouse. */
    private void paintInfoButton(Graphics2D g, Rect info) {
        double r = info.width() / 2, cx = info.centreX(), cy = info.centreY();
        g.setColor(Palette.SHADOW);
        g.fill(TokenPainter.circle(cx + r * 0.06, cy + r * 0.12, r));
        g.setColor(infoHover || legendOpen ? new Color(225, 235, 255) : Color.WHITE);
        g.fill(TokenPainter.circle(cx, cy, r));
        g.setColor(new Color(40, 46, 60));
        g.setStroke(new BasicStroke((float) Math.max(1.5, r * 0.12)));
        g.draw(TokenPainter.circle(cx, cy, r * 0.94));
        g.setFont(new Font(Font.SERIF, Font.BOLD, (int) Math.max(10, r * 1.3)));
        BoardPainter.centred(g, "i", cx, cy);
    }

    /** The static board, drawn once per size at the screen's real resolution (Windows scaling). */
    private void paintBoardImage(Graphics2D g, Rect board, double cell) {
        double scale = g.getTransform().getScaleX();
        double margin = cell * 0.2;
        double key = board.width() * 1000 + scale;
        if (boardImage == null || key != boardImageKey) {
            int px = (int) Math.ceil((board.width() + 2 * margin) * scale);
            boardImage = new BufferedImage(px, px, BufferedImage.TYPE_INT_ARGB);
            Graphics2D ig = boardImage.createGraphics();
            ig.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            ig.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            ig.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
            ig.scale(scale, scale);
            boardPainter.paintBoard(ig, margin, margin, cell);
            ig.dispose();
            boardImageKey = key;
        }
        AffineTransform at = new AffineTransform();
        at.translate(board.x() - margin, board.y() - margin);
        at.scale(1 / scale, 1 / scale);
        g.drawImage(boardImage, at, null);
    }

    // --- tokens ---

    private void paintTokens(Graphics2D g, Rect board, double cell, long now) {
        hits.clear();
        double r = cell * 0.36;
        Map<String, List<PieceSnapshot>> standing = new LinkedHashMap<>();
        List<PieceSnapshot> moving = new ArrayList<>();
        for (PieceSnapshot piece : pieces()) {
            if (animator.pose(piece.color(), piece.number(), now).isPresent())
                moving.add(piece);
            else
                standing.computeIfAbsent(groupKey(piece), k -> new ArrayList<>()).add(piece);
        }
        for (List<PieceSnapshot> group : standing.values()) {
            PieceSnapshot first = group.get(0);
            Spot spot = spotOf(first.color(), first.number(), Place.of(first));
            double cx = board.x() + spot.col() * cell, cy = board.y() + spot.row() * cell;
            double radius = first.isHome() ? cell * 0.21 : r;
            if (group.size() == 1)
                TokenPainter.paint(g, first.color(), first.number(), first, cx, cy, radius, 1);
            else
                TokenPainter.paintStack(g, group, cx, cy, radius);
            String text = group.size() == 1 ? TokenText.piece(first)
                    : "<html>Block of " + group.size() + "<br>"
                      + group.stream().map(TokenText::piece).collect(Collectors.joining("<br>")) + "</html>";
            hits.add(new Hit(TokenPainter.circle(cx, cy, radius * 1.1), text));
        }
        for (PieceSnapshot piece : moving) {
            Animator.Pose pose = animator.pose(piece.color(), piece.number(), now).orElseThrow();
            Spot a = spotOf(piece.color(), piece.number(), pose.from());
            Spot b = spotOf(piece.color(), piece.number(), pose.to());
            double cx = board.x() + (a.col() + (b.col() - a.col()) * pose.t()) * cell;
            double cy = board.y() + (a.row() + (b.row() - a.row()) * pose.t()) * cell;
            if (pose.flash() > 0) {
                double fr = r * (1.2 + (1 - pose.flash()) * 1.4);
                g.setColor(Palette.withAlpha(Palette.MYSTERY, pose.flash() * 0.8));
                g.setStroke(new BasicStroke((float) (cell * 0.08)));
                g.draw(TokenPainter.circle(cx, cy, fr));
            }
            TokenPainter.paint(g, piece.color(), piece.number(), piece, cx, cy, r * pose.scale(), pose.alpha());
        }
    }

    private List<PieceSnapshot> pieces() {
        if (snapshot != null)
            return snapshot.pieces();
        List<PieceSnapshot> waiting = new ArrayList<>(); // before the first STATE: everyone in base
        for (PlayerColor colour : PlayerColor.values())
            for (int n = 1; n <= BoardConstants.PIECES_PER_PLAYER; n++)
                waiting.add(new PieceSnapshot(colour, n, PieceLocation.BASE, -1, Direction.CLOCKWISE, 0, EffectKind.NONE, 0, false));
        return waiting;
    }

    /** Tokens on the same square share a key (a block); base and Home slots are one token each. */
    private static String groupKey(PieceSnapshot p) {
        return switch (p.location()) {
            case MAIN_PATH -> "cell" + p.position();
            case HOME_STRAIGHT -> p.color() + "hs" + p.position();
            default -> p.color().name() + p.location() + p.number();
        };
    }

    private Spot spotOf(PlayerColor colour, int number, Place place) {
        return switch (place.location()) {
            case BASE -> layout.baseSlots(colour).get(number - 1);
            case HOME -> layout.homeSlots(colour).get(number - 1);
            case MAIN_PATH -> centre(layout.cell(place.position()));
            case HOME_STRAIGHT -> centre(layout.homeStraight(colour, place.position()));
        };
    }

    private static Spot centre(GridPos pos) {
        return new Spot(pos.row() + 0.5, pos.col() + 0.5);
    }

    // --- player boxes ---

    private void paintBox(Graphics2D g, PlayerColor colour, Rect box, boolean active, long now) {
        double x = box.x(), y = box.y(), w = box.width(), h = box.height();
        double arc = h * 0.22;
        if (active) {
            double pulse = 0.5 + 0.5 * Math.sin(now / 220.0);
            for (int i = 4; i >= 1; i--) {
                double grow = h * 0.035 * i * (0.7 + 0.3 * pulse);
                g.setColor(Palette.withAlpha(Palette.board(colour), 0.10 + 0.06 * pulse));
                g.fill(new RoundRectangle2D.Double(x - grow, y - grow, w + 2 * grow, h + 2 * grow, arc + grow, arc + grow));
            }
        }
        RoundRectangle2D shape = new RoundRectangle2D.Double(x, y, w, h, arc, arc);
        g.setColor(Palette.SHADOW);
        g.fill(new RoundRectangle2D.Double(x + 2, y + 4, w, h, arc, arc));
        g.setColor(Color.WHITE);
        g.fill(shape);
        // Coloured band on the side facing the board.
        Rectangle2D band = new Rectangle2D.Double(x, y, w, h * 0.09);
        Composite savedClip = g.getComposite();
        java.awt.Shape oldClip = g.getClip();
        g.clip(shape);
        g.setColor(Palette.board(colour));
        g.fill(band);
        g.setClip(oldClip);
        g.setComposite(savedClip);
        g.setColor(active ? Palette.token(colour) : new Color(180, 185, 192));
        g.setStroke(new BasicStroke((float) (active ? Math.max(2.5, h * 0.03) : 1.2)));
        g.draw(shape);

        double pad = h * 0.12;
        // Title: the colour name only ("Red"), shrunk until it fits the box.
        String title = colour.display();
        Font font = fit(g, title, new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(10, h * 0.19)), w - 2 * pad);
        g.setFont(font);
        FontMetrics fm = g.getFontMetrics();
        double titleY = y + h * 0.09 + pad + fm.getAscent();
        g.setColor(Palette.token(colour).darker());
        g.drawString(title, (float) (x + pad), (float) titleY);

        // Dice, bottom-right, in the room left under the title.
        double below = y + h - pad - (titleY + fm.getDescent() + pad * 0.5);
        double dieSize = Math.max(8, Math.min(below, w * 0.3));
        double dx = x + w - pad - dieSize, dy = y + h - pad - dieSize;
        int value = dice.getOrDefault(colour, 0);
        double angle = 0;
        if (colour == lastRoller && tumbling(now)) {
            int frame = (int) Math.min(TUMBLE_FRAMES - 1, (now - tumbleStart) * TUMBLE_FRAMES / TUMBLE_MS);
            value = tumbleFaces[frame];
            angle = Math.sin(frame * 1.7) * 0.35;
        }
        DicePainter.paint(g, dx, dy, dieSize, value, angle);

        // Tags and Home progress, bottom-left.
        double tagH = h * 0.18;
        double tx = x + pad, ty = dy + dieSize - tagH;
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, (int) Math.max(9, tagH * 0.62)));
        int place = snapshot == null ? 0 : snapshot.finishPositions().getOrDefault(colour, 0);
        if (me.plays(colour))
            tx = tag(g, "YOU", tx, ty - tagH * 1.25, tagH, Palette.token(colour), Palette.textOn(colour));
        if (substituted.contains(colour))
            tx = tag(g, "computer", tx, ty - tagH * 1.25, tagH, new Color(90, 96, 110), Color.WHITE);
        if (place > 0)
            tag(g, Banner.ordinal(place) + " place", tx, ty - tagH * 1.25, tagH, new Color(255, 196, 0), Color.BLACK);
        long home = snapshot == null ? 0 : snapshot.piecesOf(colour).stream().filter(PieceSnapshot::isHome).count();
        g.setColor(new Color(70, 75, 85));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, (int) Math.max(9, tagH * 0.68)));
        g.drawString("Home", (float) (x + pad), (float) (ty + tagH * 0.8));
        double dotX = x + pad + g.getFontMetrics().stringWidth("Home") + tagH * 0.75;
        for (int i = 0; i < BoardConstants.PIECES_PER_PLAYER; i++) {
            Ellipse2D dot = TokenPainter.circle(dotX + i * tagH * 0.75, ty + tagH * 0.5, tagH * 0.27);
            g.setColor(i < home ? Palette.token(colour) : new Color(220, 223, 228));
            g.fill(dot);
        }

        if (!active) {
            g.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.38f));
            g.setColor(Palette.TABLE);
            g.fill(shape);
            g.setComposite(AlphaComposite.SrcOver);
        }
    }

    private static double tag(Graphics2D g, String text, double x, double y, double h, Color fill, Color fg) {
        double w = g.getFontMetrics().stringWidth(text) + h * 0.8;
        g.setColor(fill);
        g.fill(new RoundRectangle2D.Double(x, y, w, h, h, h));
        g.setColor(fg);
        BoardPainter.centred(g, text, x + w / 2, y + h / 2);
        return x + w + h * 0.3;
    }

    static Font fit(Graphics2D g, String text, Font font, double width) {
        Font f = font;
        while (f.getSize() > 9 && g.getFontMetrics(f).stringWidth(text) > width)
            f = f.deriveFont((float) (f.getSize() - 1));
        return f;
    }

    // --- hover texts ---

    @Override
    public String getToolTipText(MouseEvent e) {
        if (legendOpen)
            return null;
        if (onInfo(e.getX(), e.getY()))
            return "What the symbols mean";
        if (onWinnerClose(e.getX(), e.getY()))
            return "Close (Esc)";
        for (int i = hits.size() - 1; i >= 0; i--)
            if (hits.get(i).area().contains(e.getX(), e.getY()))
                return hits.get(i).text();
        if (table == null)
            return null;
        for (PlayerColor colour : PlayerColor.values())
            if (table.box(colour).contains(e.getX(), e.getY()))
                return colour.display() + (me.plays(colour) ? " (you)" : "")
                        + (substituted.contains(colour) ? " · played by the computer" : "");
        Rect board = table.board();
        if (!board.contains(e.getX(), e.getY()))
            return null;
        GridPos pos = new GridPos((int) ((e.getY() - board.y()) / table.cell()), (int) ((e.getX() - board.x()) / table.cell()));
        Optional<Integer> cell = layout.cellAt(pos);
        if (cell.isPresent())
            return TokenText.cell(cell.get(), snapshot == null ? null : snapshot.mystery());
        Optional<PlayerColor> straight = layout.homeStraightAt(pos);
        if (straight.isPresent())
            for (int i = 0; i < BoardConstants.HOME_STRAIGHT_SIZE; i++)
                if (layout.homeStraight(straight.get(), i).equals(pos))
                    return TokenText.homeStraight(straight.get(), i);
        if (BoardLayout.CENTRE.contains(pos))
            return "Home";
        return layout.baseAt(pos).map(c -> c.display() + " base").orElse(null);
    }

    /** True while something on the table is still moving (the window keeps its timer running anyway). */
    boolean animating() {
        long now = System.currentTimeMillis();
        return animator.busy(now) || tumbling(now) || overlays.busy();
    }

    /** For tests and the window: the latest applied snapshot. */
    GameSnapshot snapshot() {
        return snapshot;
    }
}
