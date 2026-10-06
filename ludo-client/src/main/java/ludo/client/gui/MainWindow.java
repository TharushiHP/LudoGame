package ludo.client.gui;

import ludo.client.control.Identity;
import ludo.client.net.ConnectionState;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.ResumedEvent;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;

import javax.swing.BorderFactory;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JSplitPane;
import javax.swing.WindowConstants;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.EnumMap;
import java.util.Map;

/**
 * The client's main window: header, board with legend, one panel per player, the game log and
 * the status bar (Composite: it only arranges the panels and passes each update on).
 * Every method runs on the Event Dispatch Thread; {@link SwingGameView} sees to that.
 */
public final class MainWindow extends JFrame {

    private final HeaderPanel header;
    private final BoardPanel board = new BoardPanel();
    private final Map<PlayerColor, PlayerPanel> players = new EnumMap<>(PlayerColor.class);
    private final EventLogPanel log = new EventLogPanel();
    private final StatusBar status = new StatusBar();

    public MainWindow(Identity me, String gameId, String server) {
        super("LUDO-T · game " + gameId + " · " + (me.isSpectator() ? "spectator" : me.colour().display())
                + " · " + server);
        header = new HeaderPanel(me);
        JPanel boardWithLegend = new JPanel(new BorderLayout());
        boardWithLegend.add(board, BorderLayout.CENTER);
        boardWithLegend.add(new BoardPanel.Legend(), BorderLayout.SOUTH);

        JPanel playerColumn = new JPanel(new GridLayout(4, 1, 0, 6));
        playerColumn.setBorder(BorderFactory.createEmptyBorder(0, 6, 0, 6));
        // Clockwise order of play round the board: Yellow, Blue, Red, Green.
        for (PlayerColor colour : new PlayerColor[]{PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN}) {
            PlayerPanel panel = new PlayerPanel(colour, me.plays(colour));
            players.put(colour, panel);
            playerColumn.add(panel);
        }
        playerColumn.setPreferredSize(new Dimension(380, 630));

        JPanel top = new JPanel(new BorderLayout());
        top.add(boardWithLegend, BorderLayout.CENTER);
        top.add(playerColumn, BorderLayout.EAST);
        JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, top, log);
        split.setResizeWeight(0.8);
        log.setPreferredSize(new Dimension(1000, 170));

        getContentPane().add(header, BorderLayout.NORTH);
        getContentPane().add(split, BorderLayout.CENTER);
        getContentPane().add(status, BorderLayout.SOUTH);
        // Dispose, not exit: when the window is gone only daemon threads remain and the JVM ends by itself.
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        pack();
        setLocationByPlatform(true);
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

    void applyState(StateEvent state, boolean synced) {
        header.showState(state, synced);
        board.show(state.snapshot());
        players.values().forEach(panel -> panel.show(state.snapshot()));
        log.append(state.log());
    }

    void showRequest(ServerEvent request) {
        header.showRequest(request);
    }

    void showPaused(PausedEvent paused) {
        status.showPaused(paused.colour(), paused.reason());
    }

    void showResumed(ResumedEvent resumed) {
        status.hidePause();
        if (resumed.substituted())
            players.get(resumed.colour()).markSubstituted();
    }

    void showGameOver(GameOverEvent over) {
        status.showGameOver(over);
    }

    void showConnection(ConnectionState state) {
        status.showConnection(state);
    }

    void showWarning(String message) {
        status.showWarning(message);
    }
}
