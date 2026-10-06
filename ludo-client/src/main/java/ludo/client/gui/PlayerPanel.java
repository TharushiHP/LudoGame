package ludo.client.gui;

import ludo.shared.Direction;
import ludo.shared.EffectKind;
import ludo.shared.PlayerColor;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.PieceSnapshot;

import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import java.awt.Color;
import java.util.List;

/**
 * One player's summary: pieces at base, on the board and home; each piece's place, captures and
 * active effect with rounds left; finishing place; and "played by server" once substituted.
 * Used on the Event Dispatch Thread only.
 */
final class PlayerPanel extends JPanel {

    private final PlayerColor colour;
    private final JLabel counts = new JLabel(" ");
    private final JLabel place = new JLabel(" ");
    private final JLabel[] pieces = new JLabel[4];
    private boolean substituted;

    PlayerPanel(PlayerColor colour, boolean isMe) {
        this.colour = colour;
        setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
        setBackground(Palette.light(colour));
        String title = colour.display() + " · " + Palette.role(colour) + (isMe ? " · YOU" : "");
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createTitledBorder(BorderFactory.createLineBorder(Palette.of(colour), isMe ? 4 : 2),
                        title, 0, 0, Palette.BOLD, Palette.of(colour).darker()),
                BorderFactory.createEmptyBorder(2, 6, 4, 6)));
        counts.setFont(Palette.BOLD);
        add(counts);
        for (int i = 0; i < pieces.length; i++) {
            pieces[i] = new JLabel(" ");
            pieces[i].setFont(Palette.BASE.deriveFont(13f));
            add(pieces[i]);
        }
        place.setFont(Palette.BOLD);
        add(place);
    }

    void show(GameSnapshot snapshot) {
        List<PieceSnapshot> mine = snapshot.piecesOf(colour);
        long base = mine.stream().filter(PieceSnapshot::isAtBase).count();
        long home = mine.stream().filter(PieceSnapshot::isHome).count();
        counts.setText("Base " + base + " · Board " + (mine.size() - base - home) + " · Home " + home);
        for (int i = 0; i < pieces.length && i < mine.size(); i++)
            pieces[i].setText(describe(mine.get(i)));
        int finished = snapshot.finishPositions().getOrDefault(colour, 0);
        StringBuilder status = new StringBuilder();
        if (finished > 0)
            status.append("Finished ").append(Palette.ordinal(finished)).append("   ");
        if (substituted)
            status.append("played by server");
        place.setText(status.length() == 0 ? " " : status.toString());
        place.setForeground(substituted ? Palette.BAD : Color.BLACK);
    }

    void markSubstituted() {
        substituted = true;
        place.setText("played by server");
        place.setForeground(Palette.BAD);
    }

    static String describe(PieceSnapshot p) {
        StringBuilder text = new StringBuilder(BoardPanel.label(p)).append(": ");
        switch (p.location()) {
            case BASE -> text.append("base");
            case HOME -> text.append("home ✔");
            case MAIN_PATH -> text.append("cell ").append(p.position())
                    .append(p.direction() == Direction.CLOCKWISE ? " ↻" : " ↺");
            case HOME_STRAIGHT -> text.append("home straight ").append(p.position() + 1).append("/5");
        }
        if (p.inBlock())
            text.append(" · block");
        if (p.captureCount() > 0)
            text.append(" · ").append(p.captureCount()).append(p.captureCount() == 1 ? " capture" : " captures");
        if (p.effect() != EffectKind.NONE)
            text.append(" · ").append(Palette.effect(p.effect())).append(" (").append(p.effectRoundsLeft()).append(" rounds)");
        return text.toString();
    }
}
