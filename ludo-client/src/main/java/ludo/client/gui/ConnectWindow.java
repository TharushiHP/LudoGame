package ludo.client.gui;

import ludo.client.control.Identity;
import ludo.client.net.GameSummary;
import ludo.client.net.HttpServerGateway;
import ludo.client.net.ServerGateway;
import ludo.shared.PlayerColor;

import javax.swing.BorderFactory;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.table.AbstractTableModel;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * First window when the client starts without --game: choose the server, pick or create a game,
 * pick a colour that is still free (or spectate), then Connect. Network calls run asynchronously
 * through the {@link ServerGateway}; their results come back to the Event Dispatch Thread with
 * {@code invokeLater}, so the window never freezes.
 */
public final class ConnectWindow extends JFrame {

    /** What the user chose. {@code identity} has no colour for a spectator. */
    public record Choice(String server, String gameId, Identity identity) {
    }

    private static final String SPECTATE = "Spectate";

    private final JTextField server = new JTextField(24);
    private final GamesModel games = new GamesModel();
    private final JTable table = new JTable(games);
    private final Map<PlayerColor, JRadioButton> colourButtons = new EnumMap<>(PlayerColor.class);
    private final JRadioButton spectate = new JRadioButton(SPECTATE);
    private final JTextField name = new JTextField(14);
    private final JButton connect = new JButton("Connect");
    private final JLabel message = new JLabel(" ");
    private final Consumer<Choice> onConnect;

    public ConnectWindow(String defaultServer, Consumer<Choice> onConnect) {
        super("LUDO-T · connect to a game");
        this.onConnect = onConnect;
        server.setText(defaultServer);
        server.setFont(Palette.BASE);

        JPanel serverRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        serverRow.add(bold("Server:"));
        serverRow.add(server);
        JButton refresh = new JButton("Refresh");
        refresh.addActionListener(e -> refresh(null));
        serverRow.add(refresh);
        JButton newGame = new JButton("New game...");
        newGame.addActionListener(e -> newGame());
        serverRow.add(newGame);

        table.setFont(Palette.BASE);
        table.setRowHeight(24);
        table.getTableHeader().setFont(Palette.BOLD);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.getSelectionModel().addListSelectionListener(e -> updateColours());
        JScrollPane tableScroll = new JScrollPane(table);
        tableScroll.setPreferredSize(new Dimension(640, 200));
        tableScroll.setBorder(BorderFactory.createTitledBorder(BorderFactory.createEtchedBorder(), "Games on this server",
                0, 0, Palette.BOLD));

        ButtonGroup group = new ButtonGroup();
        JPanel colourRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        colourRow.add(bold("Play as:"));
        for (PlayerColor colour : PlayerColor.values()) {
            JRadioButton button = new JRadioButton(colour.display());
            button.setFont(Palette.BOLD);
            button.setForeground(Palette.of(colour).darker());
            button.addActionListener(e -> updateConnect());
            group.add(button);
            colourButtons.put(colour, button);
            colourRow.add(button);
        }
        spectate.setFont(Palette.BOLD);
        spectate.addActionListener(e -> updateConnect());
        group.add(spectate);
        colourRow.add(spectate);

        JPanel nameRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        nameRow.add(bold("Name:"));
        name.setFont(Palette.BASE);
        nameRow.add(name);
        connect.setFont(Palette.BOLD);
        connect.addActionListener(e -> connect());
        nameRow.add(connect);

        message.setFont(Palette.BASE);
        message.setBorder(BorderFactory.createEmptyBorder(4, 8, 8, 8));
        JPanel bottom = new JPanel(new GridLayout(3, 1));
        bottom.add(colourRow);
        bottom.add(nameRow);
        bottom.add(message);

        getContentPane().add(serverRow, BorderLayout.NORTH);
        getContentPane().add(tableScroll, BorderLayout.CENTER);
        getContentPane().add(bottom, BorderLayout.SOUTH);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
        updateColours();
        refresh(null);
    }

    private ServerGateway gateway() {
        return new HttpServerGateway(server.getText());
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
                return;
            }
            games.set(list);
            message.setText(list.isEmpty() ? "No games yet: press \"New game...\"" : list.size() + " game(s). Pick one and a colour.");
            for (int row = 0; row < list.size(); row++)
                if (list.get(row).gameId().equals(keep))
                    table.setRowSelectionInterval(row, row);
            if (table.getSelectedRow() < 0 && !list.isEmpty())
                table.setRowSelectionInterval(list.size() - 1, list.size() - 1);
            updateColours();
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

    /** Only colours not yet taken in the selected game can be chosen. */
    private void updateColours() {
        Optional<GameSummary> game = selected();
        boolean waiting = game.map(g -> g.state().equals("WaitingForPlayers")).orElse(false);
        colourButtons.forEach((colour, button) -> {
            boolean free = game.isPresent() && !game.get().isTaken(colour) && waiting;
            button.setEnabled(free);
            button.setToolTipText(free ? null : game.isEmpty() ? "Pick a game first"
                    : !waiting ? "This game has started; you can only spectate" : colour.display() + " is already taken");
            if (!free && button.isSelected())
                spectate.setSelected(true);
        });
        spectate.setEnabled(game.isPresent());
        updateConnect();
    }

    private void updateConnect() {
        connect.setEnabled(selected().isPresent() && chosenColour().isPresent());
    }

    private void connect() {
        Optional<GameSummary> game = selected();
        Optional<String> colourChoice = chosenColour();
        if (game.isEmpty() || colourChoice.isEmpty())
            return;
        String chosen = colourChoice.get();
        Identity identity = chosen.equals(SPECTATE) ? Identity.spectator(name.getText())
                : Identity.player(PlayerColor.valueOf(chosen.toUpperCase()), name.getText());
        String gameId = game.get().gameId();
        if (identity.isSpectator()) {
            finish(new Choice(server.getText().trim(), gameId, identity));
            return;
        }
        // Check again just before joining: someone else may have taken the colour meanwhile.
        connect.setEnabled(false);
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
                updateColours();
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

    private Optional<String> chosenColour() {
        if (spectate.isSelected() && spectate.isEnabled())
            return Optional.of(SPECTATE);
        return colourButtons.values().stream().filter(b -> b.isSelected() && b.isEnabled())
                .map(JRadioButton::getText).findFirst();
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

    /** The table of games (EDT only). */
    private static final class GamesModel extends AbstractTableModel {

        private static final String[] COLUMNS = {"Game", "State", "Joined", "Taken", "Seed", "Turn delay"};
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
                default -> g.turnDelayMs() + " ms";
            };
        }
    }
}
