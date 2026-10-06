package ludo.client.net;

import com.sun.net.httpserver.HttpExchange;
import ludo.shared.protocol.EventType;
import ludo.shared.protocol.ServerEvent;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** The listener delivers events in order and reconnects with Last-Event-ID after the stream drops. */
class EventStreamListenerTest {

    @Test
    void reconnectsWithLastEventIdAndStopsAfterGameOver() throws Exception {
        AtomicInteger connections = new AtomicInteger();
        List<String> lastEventIds = new CopyOnWriteArrayList<>();
        try (StubServer server = new StubServer("/games/1/events", exchange -> {
            lastEventIds.add(String.valueOf(exchange.getRequestHeaders().getFirst("Last-Event-ID")));
            exchange.sendResponseHeaders(200, 0);
            if (connections.incrementAndGet() == 1) {
                write(exchange, "retry: 2000\n\n: keep-alive\n\n"
                        + "id: 5\nevent: PAUSED\ndata: {\"colour\":\"BLUE\",\"reason\":\"slow\"}\n\n");
            } else {
                write(exchange, "id: 6\nevent: RESUMED\ndata: {\"colour\":\"BLUE\",\"substituted\":false}\n\n"
                        + "id: 7\nevent: GAME_OVER\ndata: {\"status\":\"ABORTED\",\"finishPositions\":{}}\n\n");
            }
            exchange.close(); // the stream ends: the first time this looks like a dropped connection
        })) {
            Recorder recorder = new Recorder();
            EventStreamListener listener = new EventStreamListener(HttpClient.newHttpClient(),
                    URI.create(server.url() + "/games/1/events"), recorder);
            listener.start();
            assertTrue(recorder.gameOver.await(10, TimeUnit.SECONDS), "events: " + recorder.events);

            assertEquals(List.of(EventType.PAUSED, EventType.RESUMED, EventType.GAME_OVER),
                    recorder.events.stream().map(ServerEvent::type).toList());
            assertEquals(List.of("null", "5"), lastEventIds, "the reconnect sends the last id it saw");
            assertTrue(recorder.states.contains(ConnectionState.RECONNECTING));
            assertTrue(recorder.closed.await(5, TimeUnit.SECONDS), "no reconnect after GAME_OVER");
            assertEquals(2, connections.get());
        }
    }

    @Test
    void theListenerThreadIsADaemon() throws Exception {
        try (StubServer server = new StubServer("/games/1/events", exchange -> {
            exchange.sendResponseHeaders(200, 0);
            write(exchange, "event: GAME_OVER\ndata: {\"status\":\"ABORTED\",\"finishPositions\":{}}\n\n");
            exchange.close();
        })) {
            Recorder recorder = new Recorder();
            EventStreamListener listener = new EventStreamListener(HttpClient.newHttpClient(),
                    URI.create(server.url() + "/games/1/events"), recorder);
            listener.start();
            assertTrue(recorder.gameOver.await(5, TimeUnit.SECONDS));
            assertTrue(recorder.threadWasDaemon, "event-stream must be a daemon thread");
            assertEquals("event-stream", recorder.threadName);
        }
    }

    private static void write(HttpExchange exchange, String text) throws IOException {
        OutputStream out = exchange.getResponseBody();
        out.write(text.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    private static final class Recorder implements EventStreamListener.Callback {
        final List<ServerEvent> events = new CopyOnWriteArrayList<>();
        final List<ConnectionState> states = new CopyOnWriteArrayList<>();
        final CountDownLatch gameOver = new CountDownLatch(1);
        final CountDownLatch closed = new CountDownLatch(1);
        volatile boolean threadWasDaemon;
        volatile String threadName;

        @Override
        public void onEvent(ServerEvent event) {
            threadWasDaemon = Thread.currentThread().isDaemon();
            threadName = Thread.currentThread().getName();
            events.add(event);
            if (event.type() == EventType.GAME_OVER)
                gameOver.countDown();
        }

        @Override
        public void onConnection(ConnectionState state) {
            states.add(state);
            if (state == ConnectionState.CLOSED)
                closed.countDown();
        }

        @Override
        public void onProblem(String message) {
        }
    }
}
