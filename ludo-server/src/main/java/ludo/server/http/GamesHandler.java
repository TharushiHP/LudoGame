package ludo.server.http;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import ludo.game.EndCondition;
import ludo.server.config.ServerConfig;
import ludo.server.config.ServerLog;
import ludo.server.coordinator.GameSession;
import ludo.server.coordinator.SessionRegistry;
import ludo.server.coordinator.StateView;
import ludo.server.coordinator.state.Reply;
import ludo.shared.PlayerColor;
import ludo.shared.json.JsonException;
import ludo.shared.json.JsonObjects;
import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.ClientRequest;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.RollCommand;
import ludo.shared.protocol.SnapshotCodec;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Handles every path under /games. It runs on the HTTP worker threads and never touches a Game:
 * it parses the request, then either reads an immutable published value (GET /state, GET /games)
 * or hands the request to the game's queue and returns the game thread's reply.
 * <pre>
 * POST /games                    {seed?, turnDelayMs?, endCondition?}  -> 201 {gameId, seed, turnDelayMs, endCondition}
 * GET  /games                                           -> {games: [...]}
 * GET  /games/{id}                                      -> one game's summary (incl. taken colours)
 * POST /games/{id}/join          {colour, clientName, triesOtherPiecesWhenBlocked}
 * POST /games/{id}/roll          {colour, turnId, expectedVersion, requestId}
 * POST /games/{id}/decision      {colour, decisionId, expectedVersion, requestId, piece | fromBase, memo?}
 * POST /games/{id}/ack           {colour, version, hash, requestId}
 * GET  /games/{id}/state                                -> {version, hash, snapshot}
 * GET  /games/{id}/events?colour=RED                    -> text/event-stream
 * </pre>
 */
final class GamesHandler implements HttpHandler {

    private final SessionRegistry registry;
    private final ServerLog log;
    private final AtomicLong streamCount = new AtomicLong();

    GamesHandler(SessionRegistry registry, ServerLog log) {
        this.registry = registry;
        this.log = log;
    }

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        try {
            route(exchange);
        } catch (JsonException e) {
            HttpReplies.send(exchange, Reply.badRequest("malformed request: " + e.getMessage()));
        } catch (RuntimeException e) {
            log.log("internal error on " + exchange.getRequestURI() + ": " + e);
            HttpReplies.send(exchange, 500, Map.of("error", "internal error: " + e.getMessage()));
        }
    }

    private void route(HttpExchange exchange) throws IOException {
        if (!registry.isAccepting()) {
            HttpReplies.send(exchange, Reply.unavailable("server is shutting down"));
            return;
        }
        String method = exchange.getRequestMethod();
        List<String> parts = pathParts(exchange);
        if (parts.isEmpty() || !parts.get(0).equals("games")) {
            HttpReplies.send(exchange, Reply.notFound("no such path: " + exchange.getRequestURI().getPath()));
            return;
        }
        if (parts.size() == 1) {
            if (method.equals("POST"))
                createGame(exchange);
            else if (method.equals("GET"))
                HttpReplies.send(exchange, 200, Map.of("games", summaries()));
            else
                methodNotAllowed(exchange, "GET, POST");
            return;
        }
        Optional<GameSession> found = registry.find(parts.get(1));
        if (found.isEmpty()) {
            HttpReplies.send(exchange, Reply.notFound("no game with id " + parts.get(1)));
            return;
        }
        GameSession session = found.get();
        String action = parts.size() == 2 ? "" : parts.get(2);
        if (parts.size() > 3) {
            HttpReplies.send(exchange, Reply.notFound("no such path: " + exchange.getRequestURI().getPath()));
            return;
        }
        switch (action) {
            case "" -> requireGet(exchange, () -> HttpReplies.send(exchange, 200, summary(session)));
            case "state" -> requireGet(exchange, () -> HttpReplies.send(exchange, 200, state(session)));
            case "events" -> requireGet(exchange, () -> openEventStream(exchange, session));
            case "join", "roll", "decision", "ack" -> {
                if (!method.equals("POST")) {
                    methodNotAllowed(exchange, "POST");
                    return;
                }
                ClientRequest request = parseRequest(action, HttpReplies.readJson(exchange));
                HttpReplies.send(exchange, session.request(request));
            }
            default -> HttpReplies.send(exchange, Reply.notFound("no such path: " + exchange.getRequestURI().getPath()));
        }
    }

    private void createGame(HttpExchange exchange) throws IOException {
        Map<String, Object> json = HttpReplies.readJson(exchange);
        Long seed = json.get("seed") == null ? null : JsonObjects.getLong(json, "seed");
        Long delay = json.get("turnDelayMs") == null ? null : JsonObjects.getLong(json, "turnDelayMs");
        if (delay != null && delay < 0) {
            HttpReplies.send(exchange, Reply.badRequest("turnDelayMs must not be negative"));
            return;
        }
        EndCondition endCondition = null;
        if (json.get("endCondition") != null) {
            try {
                endCondition = ServerConfig.parseEndCondition(String.valueOf(json.get("endCondition")));
            } catch (IllegalArgumentException e) {
                HttpReplies.send(exchange, Reply.badRequest(e.getMessage()));
                return;
            }
        }
        GameSession session = registry.create(seed, delay, endCondition);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("gameId", session.id());
        body.put("seed", session.seed());
        body.put("turnDelayMs", session.turnDelayMs());
        body.put("endCondition", session.endCondition().name());
        HttpReplies.send(exchange, 201, body);
    }

    private static ClientRequest parseRequest(String action, Map<String, Object> json) {
        return switch (action) {
            case "join" -> JoinRequest.fromJson(json);
            case "roll" -> RollCommand.fromJson(json);
            case "decision" -> DecisionReply.fromJson(json);
            default -> AckCommand.fromJson(json);
        };
    }

    /**
     * Server-Sent Events: send the headers, register the stream with the game and return. The
     * worker thread is free again at once; the game's writer thread writes the events from now on.
     */
    private void openEventStream(HttpExchange exchange, GameSession session) throws IOException {
        PlayerColor colour = colourParameter(exchange);
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.sendResponseHeaders(200, 0);
        SseSink sink = new SseSink(exchange, colour,
                "events#" + streamCount.incrementAndGet() + "(" + (colour == null ? "spectator" : colour) + ")");
        String lastEventId = exchange.getRequestHeaders().getFirst("Last-Event-ID");
        log.log("game " + session.id() + ": " + sink.name() + " opened"
                + (lastEventId == null ? "" : ", reconnecting after event " + lastEventId + " (current STATE follows)"));
        sink.send("retry: 2000\n\n");
        session.connect(sink);
    }

    private static PlayerColor colourParameter(HttpExchange exchange) {
        String query = exchange.getRequestURI().getQuery();
        if (query == null)
            return null;
        for (String pair : query.split("&")) {
            String[] keyValue = pair.split("=", 2);
            if (keyValue.length == 2 && keyValue[0].equals("colour"))
                return JsonObjects.toEnum(keyValue[1].toUpperCase(), "colour", PlayerColor.class);
        }
        return null;
    }

    private List<Object> summaries() {
        List<Object> games = new ArrayList<>();
        for (GameSession session : registry.all())
            games.add(summary(session));
        return games;
    }

    private static Map<String, Object> summary(GameSession session) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("gameId", session.id());
        json.put("state", session.stateName());
        json.put("version", session.version());
        json.put("joined", session.joined());
        List<Object> taken = new ArrayList<>();
        for (PlayerColor colour : PlayerColor.values()) // always enum order, whatever the set's order
            if (session.taken().contains(colour))
                taken.add(colour.name());
        json.put("taken", taken);
        json.put("seed", session.seed());
        json.put("turnDelayMs", session.turnDelayMs());
        json.put("endCondition", session.endCondition().name());
        return json;
    }

    /** Read from the volatile published StateView: no queue, no lock, never blocks the game. */
    private static Map<String, Object> state(GameSession session) {
        StateView view = session.latestState();
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("gameId", session.id());
        json.put("state", session.stateName());
        json.put("version", view == null ? 0 : view.version());
        json.put("hash", view == null ? null : view.hash());
        json.put("snapshot", view == null ? null : SnapshotCodec.toJson(view.snapshot()));
        return json;
    }

    private static List<String> pathParts(HttpExchange exchange) {
        List<String> parts = new ArrayList<>();
        for (String part : exchange.getRequestURI().getPath().split("/"))
            if (!part.isEmpty())
                parts.add(part);
        return parts;
    }

    private interface IoAction {
        void run() throws IOException;
    }

    private static void requireGet(HttpExchange exchange, IoAction action) throws IOException {
        if (exchange.getRequestMethod().equals("GET"))
            action.run();
        else
            methodNotAllowed(exchange, "GET");
    }

    private static void methodNotAllowed(HttpExchange exchange, String allowed) throws IOException {
        exchange.getResponseHeaders().set("Allow", allowed);
        HttpReplies.send(exchange, 405, Map.of("error", exchange.getRequestMethod() + " not allowed here; use " + allowed));
    }
}
