package ludo.client.gui;

import javax.swing.BorderFactory;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.DefaultCaret;
import java.util.List;

/**
 * The game log, exactly as the server sends it in each STATE, scrolling to the newest line.
 * Used on the Event Dispatch Thread only.
 */
final class EventLogPanel extends JScrollPane {

    private final JTextArea text = new JTextArea();

    EventLogPanel() {
        text.setEditable(false);
        text.setFont(Palette.LOG);
        text.setLineWrap(false);
        ((DefaultCaret) text.getCaret()).setUpdatePolicy(DefaultCaret.ALWAYS_UPDATE); // auto-scroll
        setViewportView(text);
        setBorder(BorderFactory.createTitledBorder(BorderFactory.createEmptyBorder(), "Game log", 0, 0, Palette.BOLD));
    }

    void append(List<String> lines) {
        if (lines.isEmpty())
            return;
        StringBuilder block = new StringBuilder();
        for (String line : lines)
            block.append(line).append('\n');
        text.append(block.toString());
        text.setCaretPosition(text.getDocument().getLength());
    }
}
