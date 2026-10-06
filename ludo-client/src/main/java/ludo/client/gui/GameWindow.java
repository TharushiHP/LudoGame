package ludo.client.gui;

import ludo.client.control.Identity;
import ludo.client.net.ConnectionState;
import ludo.shared.protocol.DecisionRequest;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.NewGameEvent;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.ResumedEvent;
import ludo.shared.protocol.RollRequest;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;
import ludo.shared.snapshot.GameStatus;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLayeredPane;
import javax.swing.KeyStroke;
import javax.swing.Timer;
import javax.swing.WindowConstants;
import java.awt.Dimension;
import java.awt.GraphicsEnvironment;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * The one game window, like a real Ludo app: the Figure 1 board, a player box with a dice at
 * each corner, glossy animated tokens, banners, toasts and a closable winner box ({@link TablePanel}),
 * with a hidden developer overlay on F2 ({@link DevOverlay}). The round "i" button opens the
 * symbol legend ({@link LegendOverlay}); a click or Esc closes it. Esc also closes the winner box.
 * <p>
 * <b>Spectator or player.</b> The demo opens this window as a <b>spectator</b>: it never ACKs,
 * so it can animate (tokens walk, the dice tumbles) without slowing the game down. The four
 * players run as separate headless clients. A player started <b>with</b> a window (e.g. on another
 * laptop) gets the same window with "YOU" on its own box, but every state is drawn at once, with
 * no walk and no tumble: its ACK means "this state is on my screen", so nothing may lag behind.
 * In both cases {@link #applyState} applies the state immediately; animation only ever catches up.
 * <p>
 * Sized to the usable screen area (logical pixels, so Windows display scaling is respected): the
 * spectator opens maximised, a player at 90 % of the screen. Closing disposes the window and stops
 * its timer; the client's other threads are daemons, so the JVM then ends (see docs/THREADS.md).
 */
public final class GameWindow extends JFrame {

    private static final int FRAME_MS = 33;

    private final TablePanel table;
    private final DevOverlay dev = new DevOverlay();
    /** Repaints about 30 times a second on the EDT for the animations and the glow (no extra thread). */
    private final Timer frames;
    /** True after the stream dropped, so the next CONNECTED shows "Reconnected". */
    private boolean reconnecting;

    public GameWindow(Identity me, String gameId, String server) {
        super("LUDO-T · game " + gameId + " · " + (me.isSpectator() ? "Spectator" : me.colour().display() + " (you)")
                + " · " + server);
        table = new TablePanel(me, me.isSpectator());
        frames = new Timer(FRAME_MS, e -> table.repaint());

        JLayeredPane layers = new JLayeredPane();
        layers.add(table, JLayeredPane.DEFAULT_LAYER);
        layers.add(dev, JLayeredPane.PALETTE_LAYER);
        layers.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                int w = layers.getWidth(), h = layers.getHeight();
                table.setBounds(0, 0, w, h);
                int devWidth = Math.max(360, Math.min(w / 2, 620));
                dev.setBounds(w - devWidth, 0, devWidth, h);
            }
        });
        setContentPane(layers);

        layers.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("F2"), "dev");
        layers.getActionMap().put("dev", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dev.setVisible(!dev.isVisible());
            }
        });
        layers.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(KeyStroke.getKeyStroke("ESCAPE"), "escape");
        layers.getActionMap().put("escape", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                table.escape(); // the legend first, then the winner box
            }
        });

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setMinimumSize(new Dimension(640, 480));
        fitOnScreen(me.isSpectator());
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowOpened(WindowEvent e) {
                frames.start();
            }

            @Override
            public void windowClosed(WindowEvent e) {
                frames.stop(); // a running Swing timer would keep the EDT, and so the JVM, alive
            }
        });
    }

    /** The usable screen area (without the taskbar), in the logical pixels Swing works in. */
    private void fitOnScreen(boolean maximise) {
        Rectangle usable = GraphicsEnvironment.getLocalGraphicsEnvironment().getMaximumWindowBounds();
        int w = (int) (usable.width * 0.9), h = (int) (usable.height * 0.9);
        setBounds(usable.x + (usable.width - w) / 2, usable.y + (usable.height - h) / 2, w, h);
        if (maximise)
            setExtendedState(getExtendedState() | MAXIMIZED_BOTH);
    }

    /** Runs {@code action} once the window has been closed (used to stop the client's threads). */
    public void whenClosed(Runnable action) {
        addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                action.run();
            }
        });
    }

    // --- called by SwingGameView, always on the EDT ---

    void applyState(StateEvent state, boolean synced) {
        table.apply(state);
        dev.showState(state, synced);
        if (!synced)
            dev.append("! my hash differs from the server's for v" + state.version() + "; the server will send it again");
    }

    void showRequest(ServerEvent request) {
        if (request instanceof RollRequest roll)
            table.request(roll.colour());
        else if (request instanceof DecisionRequest decision)
            table.request(decision.colour());
    }

    void showPaused(PausedEvent paused) {
        table.overlays().sticky("pause", "Waiting for " + paused.colour().display() + "...");
        dev.append("! PAUSED " + paused.colour().display() + ": " + paused.reason());
    }

    void showResumed(ResumedEvent resumed) {
        table.overlays().clear("pause");
        if (resumed.substituted()) {
            table.substituted(resumed.colour());
            table.overlays().toast(resumed.colour().display() + " is now played by the computer", System.currentTimeMillis());
        }
        dev.append("! RESUMED " + resumed.colour().display() + (resumed.substituted() ? " (played by the server from now on)" : ""));
    }

    void showGameOver(GameOverEvent over) {
        table.gameOver(over);
        table.overlays().clear("pause");
        table.overlays().clear("connection");
        dev.append("! GAME_OVER " + over.status() + " " + over.finishPositions()
                + (over.hasNextGame() ? ", next game in " + over.nextGameInMs() + " ms" : ""));
    }

    /** The server starts the next game (same seats): reset the table and say so. */
    void showNewGame(NewGameEvent newGame) {
        table.newGame();
        table.overlays().clear("pause");
        table.overlays().clear("connection");
        table.overlays().toast("Game " + newGame.gameNumber() + " starting", System.currentTimeMillis());
        dev.append("! NEW_GAME " + newGame.gameNumber() + " (seed " + newGame.seed() + ")");
    }

    void showConnection(ConnectionState state) {
        dev.showConnection(state);
        switch (state) {
            case RECONNECTING -> {
                reconnecting = true;
                table.overlays().sticky("connection", "Reconnecting...");
            }
            case CONNECTED -> {
                table.overlays().clear("connection");
                if (reconnecting)
                    table.overlays().toast("Reconnected", System.currentTimeMillis());
                reconnecting = false;
            }
            case CLOSED -> {
                if (table.snapshot() == null || table.snapshot().status() == GameStatus.IN_PROGRESS)
                    table.overlays().sticky("connection", "Disconnected from the server");
            }
            default -> { }
        }
    }

    /** Technical messages go to the developer overlay only; a player sees toasts for real problems. */
    void showWarning(String message) {
        dev.append("! " + message);
    }
}
