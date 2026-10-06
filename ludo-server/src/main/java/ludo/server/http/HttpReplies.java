package ludo.server.http;

import com.sun.net.httpserver.HttpExchange;
import ludo.server.coordinator.state.Reply;
import ludo.shared.json.JsonParser;
import ludo.shared.json.JsonWriter;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

/** Reads JSON request bodies and writes JSON replies on an HttpExchange. */
final class HttpReplies {

    private HttpReplies() {}

    /** The request body as a JSON object; an empty body counts as {}. Throws JsonException if malformed. */
    static Map<String, Object> readJson(HttpExchange exchange) throws IOException {
        String body;
        try (InputStream in = exchange.getRequestBody()) {
            body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        return body.isBlank() ? new LinkedHashMap<>() : JsonParser.parseObject(body);
    }

    static void send(HttpExchange exchange, Reply reply) throws IOException {
        send(exchange, reply.status(), reply.body());
    }

    static void send(HttpExchange exchange, int status, Object jsonBody) throws IOException {
        byte[] bytes = JsonWriter.write(jsonBody).getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }
}
