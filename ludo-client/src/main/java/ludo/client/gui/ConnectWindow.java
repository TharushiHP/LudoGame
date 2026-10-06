package ludo.client.gui;

import ludo.client.control.Identity;
import ludo.client.net.GameSummary;
import ludo.client.net.HttpServerGateway;
import ludo.client.net.ServerGateway;
import ludo.shared.PlayerColor;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.RenderingHints;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * First window when the client starts without --game, styled like the game: pick the server and
 * a game (or create one), then one of two clear choices: <b>Watch game</b> (spectator) or
 * <b>Play as Red / Green / Yellow / Blue</b> (only colours still free in a game that has not
 * started). Network calls run asynchronously through the {@link ServerGateway}; their results
 * come back to the Event Dispatch Thread with {@code invokeLater}, so the window never freezes.
 */
public final class ConnectWindow extends JFrame {

    /** What the user chose. {@code identity} has no colour for a spectator. */
    public record Choice(String server, String gameId, Identity identity) {
    }

    private static final Color DARK = new Color(32, 38, 50);

    private final JTextField server = new JTextField(22);
    private final GamesModel games = new GamesModel();
    private final JTable table = new JTable(games);
    private final Map<PlayerColor, JButton> playButtons = new EnumMap<>(PlayerColor.class);
    private final JButton watch = button("Watch game", DARK, Color.WHITE);
    private final JTextField name = new JTextField(16);
    private final JLabel message = new JLabel(" ");
    private final Consumer<Choice> onConnect;

    public ConnectWindow(String defaultServer, Consumer<Choice> onConnect) {
        super("LUDO-T · connect to a game");
        this.onConnect = onConnect;
        server.setText(defaultServer);
        server.setFont(Palette.BASE);
        name.setFont(Palette.BASE);

        JPanel content = new JPanel(new BorderLayout(0, 12));
        content.setBackground(Palette.TABLE);
        content.setBorder(BorderFactory.createEmptyBorder(0, 0, 14, 0));
        content.add(new Header(), BorderLayout.NORTH);

        JPanel middle = column();
        JPanel serverRow = row();
        serverRow.add(bold("Server"));
        serverRow.add(server);
        JButton refresh = button("Refresh", Color.WHITE, DARK);
        refresh.addActionListener(e -> refresh(null));
        serverRow.add(refresh);
        JButton newGame = button("New game...", Color.WHITE, DARK);
        newGame.addActionListener(e -> newGame());
        serverRow.add(newGame);
        middle.add(serverRow);

        table.setFont(Palette.BASE);
        table.setRowHeight(26);
        table.getTableHeader().setFont(Palette.BOLD);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> updateButtons());
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(660, 170));
        tableScroll.setAlignmentX(LEFT_ALIGNMENT);
        JPanel tableWrap = row();
        tableWrap.setLayout(new BorderLayout());
        tableWrap.add(tableScroll);
        middle.add(tableWrap);

        JPanel nameRow = row();
        nameRow.add(bold("Your name"));
        nameRow.add(name);
        JLabel hint = new JLabel("(optional)");
        hint.setFont(Palette.BASE);
        hint.setForeground(Color.GRAY);
        nameRow.add(hint);
        middle.add(nameRow);
        content.add(middle, BorderLayout.CENTER);

        JPanel choices = column();
        JPanel watchRow = row();
        watch.setFont(Palette.BOLD.deriveFont(17f));
        watch.setToolTipText("Watch the game live with animations; you do not take part");
        watch.addActionListener(e -> connect(Identity.spectator(name.getText())));
        watchRow.add(watch);
        JLabel or = new JLabel("   or play as");
        or.setFont(Palette.BOLD.deriveFont(15f));
        watchRow.add(or);
        JPanel colours = new JPanel(new GridLayout(1, 4, 8, 0));
        colours.setOpaque(false);
        for (PlayerColor colour : new PlayerColor[]{PlayerColor.RED, PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.BLUE}) {
            JButton play = button(colour.display(), Palette.token(colour), Palette.textOn(colour));
            play.setFont(Palette.BOLD.deriveFont(16f));
            play.addActionListener(e -> connect(Identity.player(colour, name.getText())));
            playButtons.put(colour, play);
            colours.add(play);
        }
        watchRow.add(colours);
        choices.add(watchRow);
        message.setFont(Palette.BASE);
        JPanel messageRow = row();
        messageRow.add(message);
        choices.add(messageRow);
        content.add(choices, BorderLayout.SOUTH);

        setContentPane(content);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        updateButtons();
        refresh(null);
    }

    private ServerGateway gateway() {
        return new HttpServerGateway(server.getText().trim());
    }

    /** GET /games; then reselects {@code selectId} (or the previously selected game). */
    private void refresh(String selectId) {
        String keep = selectId != null ? selectId : selected().map(GameSummary::gameId).orElse(null);
        message.setText("Loading games...");
        ServerGateway gateway;
        try {
            gateway = gateway();
        } catch (IllegalArgumentException e) {
            message.setText("Not a valid server address: " + server.getText());
            return;
        }
        gateway.listGames().whenComplete((list, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null) {
                games.set(List.of());
                message.setText("Cannot reach " + server.getText() + " (" + rootMessage(error) + "). Is the server running?");
                updateButtons();
                return;
            }
            games.set(list);
            message.setText(list.isEmpty() ? "No games yet: press \"New game...\"" : "Pick a game, then watch it or play a free colour.");
            for (int row = 0; row < list.size(); row++)
                if (list.get(row).gameId().equals(keep))
                    table.setRowSelectionInterval(row, row);
            if (table.getSelectedRow() < 0 && !list.isEmpty())
                table.setRowSelectionInterval(list.size() - 1, list.size() - 1);
            updateButtons();
        }));
    }

    private void newGame() {
        JTextField seed = new JTextField(10);
        JTextField delay = new JTextField("500", 10);
        JPanel form = new JPanel(new GridLayout(2, 2, 6, 6));
        form.add(new JLabel("Seed (optional):"));
        form.add(seed);
        form.add(new JLabel("Turn delay in ms (optional):"));
        form.add(delay);
        if (JOptionPane.showConfirmDialog(this, form, "New game", JOptionPane.OK_CANCEL_OPTION) != JOptionPane.OK_OPTION)
            return;
        Long seedValue, delayValue;
        try {
            seedValue = seed.getText().isBlank() ? null : Long.parseLong(seed.getText().trim());
            delayValue = delay.getText().isBlank() ? null : Long.parseLong(delay.getText().trim());
        } catch (NumberFormatException e) {
            message.setText("Seed and turn delay must be whole numbers.");
            return;
        }
        gateway().createGame(seedValue, delayValue).whenComplete((reply, error) -> SwingUtilities.invokeLater(() -> {
            if (error != null)
                message.setText("Could not create a game: " + rootMessage(error));
            else if (!reply.isSuccess())
                message.setText("Could not create a game: " + reply.error());
            else
                refresh(String.valueOf(reply.body().get("gameId")));
        }));
    }

    /** Watch needs a game; a colour also needs to be free in a game that is still waiting for players. */
    private void updateButtons() {
        Optional<GameSummary> game = selected();
        boolean waiting = game.map(g -> g.state().equals("WaitingForPlayers")).orElse(false);
        playButtons.forEach((colour, button) -> {
            boolean free = game.isPresent() && !game.get().isTaken(colour) && waiting;
            button.setEnabled(free);
            button.setBackground(free ? Palette.token(colour) : Palette.mix(Palette.token(colour), Color.WHITE, 0.65));
            button.setToolTipText(free ? "Play " + colour.display()
                    : game.isEmpty() ? "Pick a game first"
                    : !waiting ? "This game has started; you can only watch" : colour.display() + " is already taken");
        });
        watch.setEnabled(game.isPresent());
        watch.setBackground(game.isPresent() ? DARK : new Color(150, 155, 165));
    }

    private void connect(Identity identity) {
        Optional<GameSummary> game = selected();
        if (game.isEmpty())
            return;
        String gameId = game.get().gameId();
        if (identity.isSpectator()) {
            finish(new Choice(server.getText().trim(), gameId, identity));
            return;
        }
        // Check again just before joining: someone else may have taken the colour meanwhile.
        playButtons.values().forEach(b -> b.setEnabled(false));
        message.setText("Checking that " + identity.colour().display() + " is still free...");
        gateway().listGames().whenComplete((list, error) -> SwingUtilities.invokeLater(() -> {
            Optional<GameSummary> now = error == null
                    ? list.stream().filter(g -> g.gameId().equals(gameId)).findFirst() : Optional.empty();
            if (now.isPresent() && !now.get().isTaken(identity.colour())) {
                finish(new Choice(server.getText().trim(), gameId, identity));
            } else {
                message.setText(identity.colour().display() + " is no longer free in game " + gameId + ". Pick another colour.");
                if (error == null)
                    games.set(list);
                updateButtons();
            }
        }));
    }

    private void finish(Choice choice) {
        dispose();
        onConnect.accept(choice);
    }

    private Optional<GameSummary> selected() {
        int row = table.getSelectedRow();
        return row < 0 || row >= games.rows.size() ? Optional.empty() : Optional.of(games.rows.get(row));
    }

    // --- look ---

    private static JButton button(String text, Color background, Color foreground) {
        JButton b = new JButton(text);
        b.setUI(new BasicButtonUI()); // plain, so the colours show on every look and feel
        b.setBackground(background);
        b.setForeground(foreground);
        b.setOpaque(true);
        b.setFocusPainted(false);
        b.setFont(Palette.BOLD);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setBorder(BorderFactory.createCompoundBorder(BorderFactory.createLineBorder(new Color(0, 0, 0, 60)),
                BorderFactory.createEmptyBorder(9, 18, 9, 18)));
        return b;
    }

    private static JPanel row() {
        JPanel p = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 4));
        p.setOpaque(false);
        p.setBorder(BorderFactory.createEmptyBorder(0, 14, 0, 14));
        p.setAlignmentX(LEFT_ALIGNMENT);
        return p;
    }

    private static JPanel column() {
        JPanel p = new JPanel();
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        p.setOpaque(false);
        p.add(Box.createVerticalStrut(2));
        return p;
    }

    private static JLabel bold(String text) {
        JLabel label = new JLabel(text);
        label.setFont(Palette.BOLD);
        return label;
    }

    private static String rootMessage(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null)
            cause = cause.getCause();
        return cause.getMessage() != null ? cause.getMessage() : cause.getClass().getSimpleName();
    }

    /** The title band: "LUDO-T" with the four player colours, as on the board. */
    private static final class Header extends JComponent {

        Header() {
            setPreferredSize(new Dimension(700, 86));
        }

        @Override
        protected void paintComponent(Graphics graphics) {
            Graphics2D g = (Graphics2D) graphics.create();
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setColor(DARK);
            g.fillRect(0, 0, getWidth(), getHeight());
            PlayerColor[] order = {PlayerColor.GREEN, PlayerColor.YELLOW, PlayerColor.RED, PlayerColor.BLUE};
            int s = 22;
            for (int i = 0; i < 4; i++) {
                g.setColor(Palette.board(order[i]));
                g.fillRect(18 + (i % 2) * (s + 3), 18 + (i / 2) * (s + 3), s, s);
            }
            g.setColor(Color.WHITE);
            g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 30));
            g.drawString("LUDO-T", 82, 48);
            g.setFont(Palette.BASE);
            g.setColor(new Color(200, 205, 215));
            g.drawString("Four automated players · one coordinator server · pick a game to watch or play", 84, 70);
            g.dispose();
        }
    }

    /** The table of games (EDT only). */
    private static final class GamesModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Game", "State", "Joined", "Taken", "Seed", "Turn delay", "Ends at"};
        private List<GameSummary> rows = new ArrayList<>();

        void set(List<GameSummary> games) {
            rows = new ArrayList<>(games);
            fireTableDataChanged();
        }

        @Override
        public int getRowCount() {
            return rows.size();
        }

        @Override
        public int getColumnCount() {
            return COLUMNS.length;
        }

        @Override
        public String getColumnName(int column) {
            return COLUMNS[column];
        }

        @Override
        public Object getValueAt(int row, int column) {
            GameSummary g = rows.get(row);
            return switch (column) {
                case 0 -> g.gameId();
                case 1 -> g.state();
                case 2 -> g.joined() + "/4";
                case 3 -> g.taken().stream().map(PlayerColor::display).collect(Collectors.joining(", "));
                case 4 -> g.seed();
                case 5 -> g.turnDelayMs() + " ms";
                default -> g.endsAtFirstWinner() ? "First winner" : "All places";
            };
        }
    }
}
