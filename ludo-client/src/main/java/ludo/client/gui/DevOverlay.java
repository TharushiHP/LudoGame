package ludo.client.gui;

import ludo.client.net.ConnectionState;
import ludo.shared.protocol.StateEvent;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.GridLayout;

/**
 * The hidden developer overlay (F2, off by default): connection state, whether this client's
 * SHA-256 of the state equals the server's, version, turn and round, and the game log exactly as
 * the server sent it (plus this client's warnings, marked "!"). A real player never needs it;
 * it is for the demo and for checking consistency. EDT only.
 */
final class DevOverlay extends JPanel {

    private static final int MAX_LOG_CHARS = 400_000;
    private static final Color TEXT = new Color(225, 230, 238);

    private final JLabel connection = label("Connection: -");
    private final JLabel sync = label("Sync: -");
    private final JLabel counters = label("Version - · Turn - · Round -");
    private final JTextArea log = new JTextArea();

    DevOverlay() {
        super(new BorderLayout(0, 8));
        setOpaque(false);
        setBorder(BorderFactory.createEmptyBorder(12, 14, 12, 14));
        JPanel top = new JPanel(new GridLayout(4, 1, 0, 2));
        top.setOpaque(false);
        JLabel title = label("Developer view (F2 to hide)");
        title.setFont(Palette.BOLD.deriveFont(15f));
        top.add(title);
        top.add(connection);
        top.add(sync);
        top.add(counters);
        log.setEditable(false);
        log.setFont(Palette.LOG);
        log.setBackground(new Color(12, 15, 20));
        log.setForeground(new Color(200, 220, 200));
        log.setCaretColor(Color.WHITE);
        JScrollPane scroll = new JScrollPane(log);
        scroll.setBorder(BorderFactory.createLineBorder(new Color(70, 76, 90)));
        add(top, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        setVisible(false);
    }

    @Override
    protected void paintComponent(Graphics g) {
        g.setColor(new Color(20, 24, 32, 225));
        g.fillRect(0, 0, getWidth(), getHeight());
        super.paintComponent(g);
    }

    void showState(StateEvent state, boolean synced) {
        String hash = state.hash().length() > 12 ? state.hash().substring(0, 12) + "..." : state.hash();
        sync.setText(synced ? "Sync: ✔ synced · SHA-256 " + hash : "Sync: ✖ OUT OF SYNC · server hash " + hash);
        sync.setForeground(synced ? new Color(120, 220, 130) : new Color(255, 110, 110));
        counters.setText("Version " + state.version() + " · Turn " + state.snapshot().turnCount()
                + " · Round " + state.snapshot().roundNumber());
        for (String line : state.log())
            append(line);
    }

    void showConnection(ConnectionState state) {
        connection.setText("Connection: " + state.display());
    }

    void append(String line) {
        log.append(line + "\n");
        if (log.getDocument().getLength() > MAX_LOG_CHARS)
            log.replaceRange("", 0, log.getDocument().getLength() - MAX_LOG_CHARS / 2);
        log.setCaretPosition(log.getDocument().getLength());
    }

    private static JLabel label(String text) {
        JLabel label = new JLabel(text);
        label.setForeground(TEXT);
        label.setFont(Palette.BASE.deriveFont(Font.PLAIN, 13f));
        return label;
    }
}
