package ludo.client.gui;

import ludo.client.control.Identity;
import ludo.client.gui.model.TableLayout;
import ludo.client.gui.model.WinnerBox;
import ludo.shared.Direction;
import ludo.shared.EffectKind;
import ludo.shared.PieceLocation;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.StateEvent;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;
import ludo.shared.snapshot.MysterySnapshot;
import ludo.shared.snapshot.PieceSnapshot;
import org.junit.jupiter.api.Test;

import java.awt.Graphics2D;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The game table, drawn off-screen: hover texts, animation only in the spectator window, the
 * legend button, and the winner screens.
 */
class TablePanelTest {

    private static final int W = 1200, H = 700;

    private static GameSnapshot snapshotWithRedOn(int cell) {
        List<PieceSnapshot> pieces = new ArrayList<>();
        for (PlayerColor colour : new PlayerColor[]{PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN})
            for (int n = 1; n <= 4; n++) {
                boolean r1 = colour == PlayerColor.RED && n == 1;
                pieces.add(new PieceSnapshot(colour, n, r1 ? PieceLocation.MAIN_PATH : PieceLocation.BASE, r1 ? cell : -1,
                        Direction.CLOCKWISE, r1 ? 1 : 0, r1 ? EffectKind.SICK : EffectKind.NONE, r1 ? 2 : 0, false));
            }
        return new GameSnapshot(3, 9, PlayerColor.RED, 4, new MysterySnapshot(-1, 0), Map.of(), GameStatus.IN_PROGRESS,
                pieces, Map.of());
    }

    private static StateEvent state(long version, int redCell) {
        return new StateEvent(version, snapshotWithRedOn(redCell), "hash", List.of("Red player rolled 4."));
    }

    private static TablePanel painted(TablePanel panel) {
        panel.setSize(W, H);
        BufferedImage image = new BufferedImage(W, H, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = image.createGraphics();
        panel.paint(g);
        g.dispose();
        return panel;
    }

    private static String hover(TablePanel panel, double x, double y) {
        return panel.getToolTipText(new MouseEvent(panel, MouseEvent.MOUSE_MOVED, 0, 0, (int) Math.round(x), (int) Math.round(y), 0, false));
    }

    private static double[] centreOf(GridPos pos) {
        TableLayout t = TableLayout.of(W, H);
        return new double[]{t.board().x() + (pos.col() + 0.5) * t.cell(), t.board().y() + (pos.row() + 0.5) * t.cell()};
    }

    @Test
    void hoveringATokenOrACellExplainsIt() {
        TablePanel panel = new TablePanel(Identity.spectator(null), true);
        panel.apply(state(1, 30));
        painted(panel);
        BoardLayout layout = new BoardLayout();
        double[] token = centreOf(layout.cell(30));
        assertEquals("R1 (Red) · cell 30 · clockwise · 1 capture · sick, 2 rounds left", hover(panel, token[0], token[1]));
        double[] start = centreOf(layout.cell(13));
        assertEquals("Cell 13 · Blue start (X)", hover(panel, start[0], start[1]));
        double[] straight = centreOf(layout.homeStraight(PlayerColor.GREEN, 0));
        assertEquals("Green home straight, square 1 of 5", hover(panel, straight[0], straight[1]));
        assertNull(hover(panel, 2, 2), "the table round the board has no hover text");
    }

    private static void click(TablePanel panel, double x, double y) {
        panel.dispatchEvent(new MouseEvent(panel, MouseEvent.MOUSE_CLICKED, 0, 0,
                (int) Math.round(x), (int) Math.round(y), 1, false, MouseEvent.BUTTON1));
    }

    @Test
    void theInfoButtonOpensTheLegendAndAnyClickClosesIt() {
        TablePanel panel = painted(new TablePanel(Identity.spectator(null), true));
        TableLayout.Rect info = TableLayout.of(W, H).info();
        assertFalse(panel.legendOpen());

        click(panel, 5, H - 5);
        assertFalse(panel.legendOpen(), "a click elsewhere does not open it");
        click(panel, info.centreX(), info.centreY());
        assertTrue(panel.legendOpen());
        painted(panel); // the legend draws without errors
        click(panel, info.centreX(), info.centreY());
        assertFalse(panel.legendOpen(), "clicking the button again closes it");

        click(panel, info.centreX(), info.centreY());
        click(panel, W / 2.0, H / 2.0);
        assertFalse(panel.legendOpen(), "a click anywhere closes it");
        click(panel, info.centreX(), info.centreY());
        panel.closeLegend(); // what Esc does
        assertFalse(panel.legendOpen());
    }

    @Test
    void aPlayerBoxShowsOnlyTheColourName() {
        TablePanel panel = painted(new TablePanel(Identity.player(PlayerColor.RED, null), false));
        TableLayout t = TableLayout.of(W, H);
        TableLayout.Rect red = t.box(PlayerColor.RED), green = t.box(PlayerColor.GREEN);
        assertEquals("Red (you)", hover(panel, red.centreX(), red.centreY()));
        assertEquals("Green", hover(panel, green.centreX(), green.centreY()));
        panel.substituted(PlayerColor.GREEN);
        assertEquals("Green · played by the computer", hover(panel, green.centreX(), green.centreY()));
    }

    @Test
    void theWinnerScreenDrawsForBothEndings() {
        TablePanel first = new TablePanel(Identity.spectator(null), false);
        first.apply(state(1, 30));
        first.gameOver(new GameOverEvent(GameStatus.FINISHED, Map.of(PlayerColor.RED, 1)));
        painted(first);
        TablePanel podium = new TablePanel(Identity.spectator(null), false);
        podium.apply(state(1, 30));
        podium.gameOver(new GameOverEvent(GameStatus.FINISHED,
                Map.of(PlayerColor.RED, 1, PlayerColor.GREEN, 2, PlayerColor.YELLOW, 3, PlayerColor.BLUE, 4)));
        painted(podium);
        TablePanel countdown = new TablePanel(Identity.spectator(null), false);
        countdown.gameOver(new GameOverEvent(GameStatus.FINISHED, Map.of(PlayerColor.RED, 1), 10_000));
        painted(countdown);
    }

    private static TablePanel afterFirstWinner() {
        TablePanel panel = new TablePanel(Identity.spectator(null), false);
        panel.apply(state(1, 30));
        panel.gameOver(new GameOverEvent(GameStatus.FINISHED, Map.of(PlayerColor.RED, 1), 10_000));
        return painted(panel);
    }

    @Test
    void theWinnerBoxClosesWithItsCloseButtonOnly() {
        TablePanel panel = afterFirstWinner();
        WinnerBox box = WinnerBox.of(TableLayout.of(W, H).board(), true);
        assertTrue(panel.winnerBoxOpen());

        click(panel, box.box().centreX(), box.box().centreY());
        assertTrue(panel.winnerBoxOpen(), "a click inside the box but not on the × keeps it open");
        assertEquals("Close (Esc)", hover(panel, box.close().centreX(), box.close().centreY()));
        click(panel, box.close().centreX(), box.close().centreY());
        assertFalse(panel.winnerBoxOpen());
        assertNotNull(panel.gameOver(), "closing only hides the box; the game is still over");
    }

    @Test
    void escClosesTheLegendFirstThenTheWinnerBox() {
        TablePanel panel = afterFirstWinner();
        TableLayout.Rect info = TableLayout.of(W, H).info();
        click(panel, info.centreX(), info.centreY());
        assertTrue(panel.legendOpen());

        panel.escape();
        assertFalse(panel.legendOpen());
        assertTrue(panel.winnerBoxOpen(), "the first Esc closes only the legend");
        panel.escape();
        assertFalse(panel.winnerBoxOpen());
    }

    @Test
    void newGameResetsTheTable() {
        TablePanel panel = afterFirstWinner();
        panel.newGame();
        assertNull(panel.snapshot(), "every token is drawn in base until the new game's first STATE");
        assertNull(panel.gameOver());
        assertFalse(panel.winnerBoxOpen());
        painted(panel);
        panel.apply(state(2, 12));
        assertEquals(12, panel.snapshot().pieces().get(8).position());
    }

    @Test
    void theSpectatorAnimatesButAPlayerWindowDrawsEachStateAtOnce() {
        TablePanel spectator = new TablePanel(Identity.spectator(null), true);
        spectator.apply(state(1, 30));
        spectator.apply(state(2, 34));
        assertTrue(spectator.animating(), "the spectator walks R1 from 30 to 34");
        assertEquals(34, spectator.snapshot().pieces().get(8).position(), "but the state itself is applied at once");

        TablePanel player = new TablePanel(Identity.player(PlayerColor.RED, null), false);
        player.apply(state(1, 30));
        player.apply(state(2, 34));
        assertFalse(player.animating(), "no walk and no dice tumble in a player window");
        assertEquals(34, player.snapshot().pieces().get(8).position());
        painted(player);
        double[] there = centreOf(new BoardLayout().cell(34));
        assertTrue(hover(player, there[0], there[1]).startsWith("R1 (Red) · cell 34"), "already drawn on its new cell");
    }
}
