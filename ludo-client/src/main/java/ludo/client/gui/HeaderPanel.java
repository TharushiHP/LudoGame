package ludo.client.gui;

import ludo.client.control.Identity;
import ludo.shared.protocol.DecisionKind;
import ludo.shared.protocol.DecisionRequest;
import ludo.shared.protocol.RollRequest;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;
import ludo.shared.snapshot.GameSnapshot;

import javax.swing.BorderFactory;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;

/**
 * Top of the main window: who this client is, turn/round/version, whose turn it is with the last
 * dice value, and the sync badge (green when this client's hash matches the server's).
 * Used on the Event Dispatch Thread only.
 */
final class HeaderPanel extends JPanel {

    private final JLabel progress = label(Palette.BOLD);
    private final JLabel turn = label(Palette.BASE);
    private final JLabel sync = new JLabel("  Waiting for state  ", SwingConstants.CENTER);

    HeaderPanel(Identity me) {
        super(new BorderLayout(12, 0));
        setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        JLabel who = new JLabel(me.headline());
        who.setFont(Palette.TITLE);
        if (!me.isSpectator())
            who.setForeground(Palette.of(me.colour()).darker());
        JPanel lines = new JPanel(new GridLayout(3, 1));
        lines.setOpaque(false);
        lines.add(who);
        lines.add(progress);
        lines.add(turn);
        add(lines, BorderLayout.CENTER);
        sync.setOpaque(true);
        sync.setFont(Palette.BOLD.deriveFont(16f));
        sync.setBackground(Color.LIGHT_GRAY);
        sync.setBorder(BorderFactory.createEmptyBorder(6, 12, 6, 12));
        add(sync, BorderLayout.EAST);
    }

    void showState(StateEvent state, boolean synced) {
        GameSnapshot s = state.snapshot();
        progress.setText("Turn " + s.turnCount() + " · Round " + s.roundNumber() + " · Version " + state.version());
        if (s.currentPlayer() != null)
            turn.setText(s.currentPlayer().display() + "'s turn · last roll " + (s.lastRoll() == 0 ? "-" : s.lastRoll()));
        else
            turn.setText("The game has not started its first turn yet");
        sync.setText(synced ? "  ✔ Synced  " : "  ✖ Out of sync  ");
        sync.setBackground(synced ? Palette.OK : Palette.BAD);
        sync.setForeground(Color.WHITE);
        sync.setToolTipText(synced ? "My SHA-256 of this state equals the server's: " + abbreviate(state.hash())
                : "My hash differs from the server's " + abbreviate(state.hash()) + "; the server will send the state again");
    }

    /** "Waiting for Blue to roll" or "Blue is choosing a piece (rolled 6)". */
    void showRequest(ServerEvent request) {
        if (request instanceof RollRequest roll) {
            turn.setText("Waiting for " + roll.colour().display() + " to roll");
        } else if (request instanceof DecisionRequest q) {
            String what = q.kind() == DecisionKind.MOVE_FROM_BASE
                    ? " is deciding whether to bring a piece out of base" : " is choosing a piece";
            turn.setText(q.colour().display() + what + " (rolled " + q.roll() + ")");
        }
    }

    private static String abbreviate(String hash) {
        return hash == null || hash.length() < 12 ? String.valueOf(hash) : hash.substring(0, 12) + "...";
    }

    private static JLabel label(java.awt.Font font) {
        JLabel label = new JLabel(" ");
        label.setFont(font);
        return label;
    }
}
