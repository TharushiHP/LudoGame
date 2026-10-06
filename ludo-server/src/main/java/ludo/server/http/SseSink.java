package ludo.server.http;

import com.sun.net.httpserver.HttpExchange;
import ludo.server.coordinator.EventSink;
import ludo.shared.PlayerColor;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * An {@link EventSink} backed by a Server-Sent Events response (Adapter: the coordinator's port
 * implemented with the JDK HTTP server). The response is chunked and stays open after the
 * handler returns; the JDK server ends an exchange only when its body stream is closed.
 */
final class SseSink implements EventSink {

    private final HttpExchange exchange;
    private final OutputStream out;
    private final PlayerColor colour;
    private final String name;

    SseSink(HttpExchange exchange, PlayerColor colour, String name) {
        this.exchange = exchange;
        this.out = exchange.getResponseBody();
        this.colour = colour;
        this.name = name;
    }

    @Override
    public PlayerColor colour() {
        return colour;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public void send(String frame) throws IOException {
        out.write(frame.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    @Override
    public void close() {
        try {
            out.close();
        } catch (IOException e) {
            // already broken; closing the exchange below releases it anyway
        }
        exchange.close();
    }
}
