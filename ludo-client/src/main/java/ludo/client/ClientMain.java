package ludo.client;

import ludo.client.console.ConsoleGameView;
import ludo.client.gui.ConnectWindow;
import ludo.client.gui.GameWindow;
import ludo.client.gui.SwingGameView;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;


public final class ClientMain {

    private ClientMain() {}

    public static void main(String[] args) throws InterruptedException {
        ClientOptions options;
        try {
            options = ClientOptions.parse(args);
        } catch (IllegalArgumentException e) {
            System.err.println(e.getMessage());
            System.err.println("usage: java -jar ludo-client.jar [--server=URL] [--game=ID "
                    + "--colour=RED|GREEN|YELLOW|BLUE|SPECTATOR] [--name=TEXT] [--headless]");
            System.exit(2);
            return;
        }
        if (options.headless()) {
            runHeadless(options);
        } else if (options.skipsConnectWindow()) {
            SwingUtilities.invokeLater(() -> openGame(new ConnectWindow.Choice(options.server(), options.gameId(), options.identity())));
        } else {
            SwingUtilities.invokeLater(() -> {
                useSystemLookAndFeel();
                new ConnectWindow(options.server(), ClientMain::openGame).setVisible(true);
            });
        }
        // main ends here; the Event Dispatch Thread (non-daemon) keeps the GUI client running.
    }

    /**
     * Headless: main waits for the session's last GAME_OVER (one without a next game), so the player
     * stays for every next game the server starts; then the JVM exits (all client threads are daemons).
     */
    private static void runHeadless(ClientOptions options) throws InterruptedException {
        ClientSession session = new ClientSession(options.server(), options.gameId(), options.identity(),
                new ConsoleGameView(System.out, options.identity()));
        session.start();
        while (!session.awaitGameOver(60_000)) {
            // keep waiting; the listener reconnects by itself if the server was briefly away
        }
        session.close();
    }

    /** Runs on the EDT: builds the window, then starts the session off the EDT (it waits for the stream). */
    private static void openGame(ConnectWindow.Choice choice) {
        useSystemLookAndFeel();
        GameWindow window = new GameWindow(choice.identity(), choice.gameId(), choice.server());
        ClientSession session = new ClientSession(choice.server(), choice.gameId(), choice.identity(), new SwingGameView(window));
        window.whenClosed(session::close);
        window.setVisible(true);
        Thread starter = new Thread(() -> {
            try {
                session.start();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "client-start");
        starter.setDaemon(true); // one-off: opens the stream and sends JOIN, then ends
        starter.start();
    }

    private static void useSystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            // the default look and feel is fine too
        }
    }
}
