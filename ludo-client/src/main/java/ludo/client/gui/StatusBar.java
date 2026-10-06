package ludo.client.gui;

import ludo.client.net.ConnectionState;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.GameOverEvent;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.util.Map;
import java.util.TreeMap;

/**
 * Bottom of the main window: a banner for "Paused: waiting for Blue" or the final placings, and a
 * line with the connection state and the latest warning. Used on the Event Dispatch Thread only.
 */
final class StatusBar extends JPanel {

    private final JLabel banner = new JLabel();
    private final JLabel connection = new JLabel(ConnectionState.CONNECTING.display());
    private final JLabel warning = new JLabel(" ");
    private boolean over;

    StatusBar() {
        super(new BorderLayout());
        banner.setOpaque(true);
        banner.setFont(Palette.BOLD.deriveFont(17f));
        banner.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        banner.setVisible(false);
        add(banner, BorderLayout.NORTH);
        JPanel line = new JPanel(new BorderLayout(16, 0));
        line.setBorder(BorderFactory.createEmptyBorder(4, 12, 4, 12));
        connection.setFont(Palette.BOLD);
        warning.setFont(Palette.BASE.deriveFont(13f));
        warning.setForeground(Color.DARK_GRAY);
        line.add(connection, BorderLayout.WEST);
        line.add(warning, BorderLayout.CENTER);
        add(line, BorderLayout.SOUTH);
    }

    void showConnection(ConnectionState state) {
        connection.setText("● " + state.display());
        connection.setForeground(state == ConnectionState.CONNECTED ? Palette.OK
                : state == ConnectionState.CLOSED && over ? Color.GRAY : Palette.BAD);
    }

    void showWarning(String message) {
        warning.setText(message);
        warning.setToolTipText(message);
    }

    void showPaused(PlayerColor colour, String reason) {
        if (over)
            return;
        banner.setText("⏸  Paused: waiting for " + colour.display() + "  (" + reason + ")");
        banner.setBackground(Palette.PAUSE);
        banner.setVisible(true);
    }

    void hidePause() {
        if (!over)
            banner.setVisible(false);
    }

    void showGameOver(GameOverEvent result) {
        over = true;
        Map<Integer, String> byPlace = new TreeMap<>();
        StringBuilder unranked = new StringBuilder();
        result.finishPositions().forEach((colour, place) -> {
            if (place > 0)
                byPlace.put(place, colour.display());
            else
                unranked.append(unranked.length() == 0 ? "" : ", ").append(colour.display());
        });
        StringBuilder text = new StringBuilder("<html>🏁 Game over (" + result.status() + ")&nbsp;&nbsp;");
        byPlace.forEach((place, name) -> text.append(Palette.ordinal(place)).append(": <b>").append(name).append("</b>&nbsp;&nbsp;"));
        if (unranked.length() > 0)
            text.append("not finished: ").append(unranked);
        banner.setText(text.append("</html>").toString());
        banner.setBackground(Palette.GAME_OVER);
        banner.setVisible(true);
    }
}
