package ludo.client.net;

import ludo.shared.json.JsonParser;
import ludo.shared.protocol.EventType;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.ServerEvent;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Iterator;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Reads the game's Server-Sent Events stream on its own <b>daemon</b> thread, {@code event-stream},
 * and hands each event to a {@link Callback} (the controller) in the order it arrived. It is a
 * daemon because a blocking read must never keep the JVM alive after the window is closed.
 * <p>
 * When the stream ends or fails, it reconnects with a Last-Event-ID header, waiting 0.5, 1, 2, 4,
 * then 5 s between attempts (exponential backoff, max 5 s; reset after a successful connect). The
 * server answers a reconnect with the current STATE and any open request, so the client catches up.
 * It stops for good after the session's last GAME_OVER (one without a next game) or {@link #close()}.
 */
public final class EventStreamListener {

    /** Receives what the listener reads. Always called on the {@code event-stream} thread. */
    public interface Callback {
        void onEvent(ServerEvent event);

        void onConnection(ConnectionState state);

        void onProblem(String message);
    }

    static final long FIRST_BACKOFF_MS = 500;
    static final long MAX_BACKOFF_MS = 5_000;

    private final HttpClient http;
    private final URI uri;
    private final Callback callback;
    private final SseFrameParser parser = new SseFrameParser();
    private final CountDownLatch firstConnect = new CountDownLatch(1);
    private final Thread thread;
    private volatile boolean closed;
    private volatile Stream<String> current;

    public EventStreamListener(HttpClient http, URI uri, Callback callback) {
        this.http = http;
        this.uri = uri;
        this.callback = callback;
        this.thread = new Thread(this::run, "event-stream");
        this.thread.setDaemon(true);
    }

    public void start() {
        thread.start();
    }

    /** Waits until the stream is open for the first time, so the JOIN can follow it. */
    public boolean awaitConnected(long timeoutMs) throws InterruptedException {
        return firstConnect.await(timeoutMs, TimeUnit.MILLISECONDS);
    }

    /** Stops reading and reconnecting (closing the stream ends the blocking read). */
    public void close() {
        closed = true;
        Stream<String> stream = current;
        if (stream != null)
            stream.close();
        thread.interrupt();
    }

    /** Converts one SSE frame into the protocol record it carries. */
    public static ServerEvent toEvent(SseFrame frame) {
        return ServerEvent.fromJson(EventType.valueOf(frame.event()), JsonParser.parseObject(frame.data()));
    }

    private void run() {
        long backoff = FIRST_BACKOFF_MS;
        callback.onConnection(ConnectionState.CONNECTING);
        while (!closed) {
            boolean finished = false;
            try {
                HttpResponse<Stream<String>> response = http.send(request(), HttpResponse.BodyHandlers.ofLines());
                if (response.statusCode() == 200) {
                    current = response.body();
                    callback.onConnection(ConnectionState.CONNECTED);
                    firstConnect.countDown();
                    backoff = FIRST_BACKOFF_MS;
                    finished = read(response.body().iterator());
                } else {
                    response.body().close();
                    callback.onProblem("event stream refused: HTTP " + response.statusCode());
                    finished = response.statusCode() == 404; // no such game: retrying cannot help
                }
            } catch (InterruptedException e) {
                break;
            } catch (IOException | RuntimeException e) {
                if (!closed)
                    callback.onProblem("event stream lost: " + e.getMessage());
            }
            if (finished || closed)
                break;
            callback.onConnection(ConnectionState.RECONNECTING);
            try {
                Thread.sleep(backoff); // this client thread waits alone; nothing else is held up
            } catch (InterruptedException e) {
                break;
            }
            backoff = Math.min(backoff * 2, MAX_BACKOFF_MS);
        }
        callback.onConnection(ConnectionState.CLOSED);
    }

    private HttpRequest request() {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri).header("Accept", "text/event-stream").GET();
        if (parser.lastEventId() != null)
            builder.header("Last-Event-ID", parser.lastEventId());
        return builder.build();
    }

    /**
     * Reads frames until the stream ends. True if a GAME_OVER without a next game arrived (then there
     * is no reconnect); after a GAME_OVER that announces a next game the same stream keeps going.
     */
    private boolean read(Iterator<String> lines) {
        while (!closed && lines.hasNext()) {
            SseFrame frame = parser.accept(lines.next());
            if (frame == null)
                continue;
            ServerEvent event;
            try {
                event = toEvent(frame);
            } catch (RuntimeException e) {
                callback.onProblem("unreadable " + frame.event() + " event: " + e.getMessage());
                continue;
            }
            callback.onEvent(event);
            if (event instanceof GameOverEvent over && !over.hasNextGame())
                return true;
        }
        return false;
    }
}
