package ludo.client;

import ludo.client.control.ClientController;
import ludo.client.control.GameView;
import ludo.client.control.Identity;
import ludo.client.net.EventStreamListener;
import ludo.client.net.HttpServerGateway;
import ludo.players.SnapshotStrategyDecider;

/**
 * Connects one client to one game (composition root for a session): builds the gateway, the
 * controller and the event-stream listener and starts them in the right order. The stream is
 * opened before the JOIN, so this client already receives the first STATE when the fourth
 * player's JOIN starts the game.
 */
public final class ClientSession {

    private static final long CONNECT_WAIT_MS = 10_000;

    private final ClientController controller;
    private final EventStreamListener listener;

    public ClientSession(String server, String gameId, Identity me, GameView view) {
        this(new HttpServerGateway(server), gameId, me, view);
    }

    /** With a gateway built by the caller, e.g. the test clients' instrumented one. */
    public ClientSession(HttpServerGateway gateway, String gameId, Identity me, GameView view) {
        this.controller = new ClientController(gateway, gameId, me, view, new SnapshotStrategyDecider());
        this.listener = new EventStreamListener(gateway.http(), gateway.eventsUri(gameId, me.colour()), controller);
    }

    /** Starts the threads and joins. Blocks for at most 10 s while the stream opens, so not on the EDT. */
    public void start() throws InterruptedException {
        controller.start();
        listener.start();
        listener.awaitConnected(CONNECT_WAIT_MS); // if it is not up yet, join anyway: the server resyncs on connect
        controller.join();
    }

    public boolean awaitGameOver(long timeoutMs) throws InterruptedException {
        return controller.awaitGameOver(timeoutMs);
    }

    public void close() {
        listener.close();
        controller.stop();
    }
}
