package ludo.testclients.client;

import ludo.client.ClientSession;
import ludo.client.control.Identity;
import ludo.client.net.HttpServerGateway;
import ludo.client.net.RequestObserver;
import ludo.client.net.RetryPolicy;
import ludo.shared.PlayerColor;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * One fast automatic player: the real thick client ({@link ClientSession}: event-stream listener,
 * controller, this colour's strategy) without a window, with its own HttpClient and an instrumented
 * gateway, so its requests are counted. It never applies a rule itself; it only answers the
 * server's requests, as every client does.
 */
public final class HeadlessPlayer {

    private final PlayerColor colour;
    private final RecordingView view = new RecordingView();
    private final ClientSession session;

    public HeadlessPlayer(String server, String gameId, PlayerColor colour, RequestObserver observer) {
        this.colour = colour;
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        HttpServerGateway gateway = new HttpServerGateway(http, server, RetryPolicy.standard(), observer);
        this.session = new ClientSession(gateway, gameId, Identity.player(colour, "Test " + colour.display()), view);
    }

    /** Opens the event stream and joins. Blocks for up to 10 s while the stream opens. */
    public void start() throws InterruptedException {
        session.start();
    }

    /** Waits for the first GAME_OVER (with rematch on, the player stops after the first game). */
    public boolean awaitGameOver(long timeoutMs) throws InterruptedException {
        return view.awaitGameOver(timeoutMs);
    }

    public void close() {
        session.close();
    }

    public PlayerColor colour() {
        return colour;
    }

    public RecordingView view() {
        return view;
    }
}
