package ludo.client.net;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonException;
import ludo.shared.json.JsonObjects;
import ludo.shared.json.JsonParser;
import ludo.shared.json.JsonWriter;
import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.ClientRequest;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.RollCommand;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.TimeUnit;

/**
 * The coordinator server as the client sees it (Remote Proxy): each {@link ServerGateway} call
 * becomes an asynchronous HTTP request ({@code HttpClient.sendAsync}) with a JSON body.
 * <p>
 * Retries: a network error or a 503 (server busy) sends the <b>same</b> {@link HttpRequest} again
 * after a growing wait ({@link RetryPolicy}). The same request means the same requestId, so if the
 * first attempt did reach the server, the server answers the retry from its idempotency map instead
 * of applying it twice. Every other reply, including 409, is returned at once and never retried.
 * The waits use {@code CompletableFuture.delayedExecutor}, so no thread blocks while waiting.
 */
public final class HttpServerGateway implements ServerGateway {

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(10);

    private final HttpClient http;
    private final URI base;
    private final RetryPolicy retry;

    public HttpServerGateway(String serverUrl) {
        this(HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(), serverUrl, RetryPolicy.standard());
    }

    public HttpServerGateway(HttpClient http, String serverUrl, RetryPolicy retry) {
        String trimmed = serverUrl.trim();
        this.http = http;
        this.base = URI.create(trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed);
        this.retry = retry;
    }

    /** The shared HttpClient, also used by the {@link EventStreamListener}. */
    public HttpClient http() {
        return http;
    }

    /** GET /games/{id}/events, with ?colour= for a player (a spectator sends none). */
    public URI eventsUri(String gameId, PlayerColor colour) {
        return URI.create(base + "/games/" + gameId + "/events" + (colour == null ? "" : "?colour=" + colour.name()));
    }

    @Override
    public CompletableFuture<List<GameSummary>> listGames() {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/games")).timeout(REQUEST_TIMEOUT).GET().build();
        return send(request, 1).thenApply(reply -> {
            if (!reply.isSuccess())
                throw new CompletionException(new IOException("GET /games: " + reply.error()));
            List<GameSummary> games = new ArrayList<>();
            for (Object game : JsonObjects.getList(reply.body(), "games"))
                games.add(GameSummary.fromJson(JsonObjects.asObject(game, "game")));
            return games;
        });
    }

    @Override
    public CompletableFuture<GatewayReply> createGame(Long seed, Long turnDelayMs) {
        Map<String, Object> body = new LinkedHashMap<>();
        if (seed != null)
            body.put("seed", seed);
        if (turnDelayMs != null)
            body.put("turnDelayMs", turnDelayMs);
        return post("/games", body);
    }

    @Override
    public CompletableFuture<GatewayReply> join(String gameId, JoinRequest join) {
        return post(gameId, join);
    }

    @Override
    public CompletableFuture<GatewayReply> roll(String gameId, RollCommand roll) {
        return post(gameId, roll);
    }

    @Override
    public CompletableFuture<GatewayReply> decision(String gameId, DecisionReply decision) {
        return post(gameId, decision);
    }

    @Override
    public CompletableFuture<GatewayReply> ack(String gameId, AckCommand ack) {
        return post(gameId, ack);
    }

    /** POST /games/{id}/join, /roll, /decision or /ack: the path is the request type's name. */
    private CompletableFuture<GatewayReply> post(String gameId, ClientRequest request) {
        return post("/games/" + gameId + "/" + request.type().name().toLowerCase(), request.toJson());
    }

    private CompletableFuture<GatewayReply> post(String path, Map<String, Object> json) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(JsonWriter.write(json)))
                .build();
        return send(request, 1);
    }

    /** Sends {@code request}; after a network error or 503 it sends the very same request again later. */
    private CompletableFuture<GatewayReply> send(HttpRequest request, int attempt) {
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                .handle((response, error) -> {
                    Throwable cause = unwrap(error);
                    boolean retryable = cause instanceof IOException
                            || (cause == null && response.statusCode() == 503);
                    if (!retryable || attempt >= retry.maxAttempts()) {
                        return cause == null
                                ? CompletableFuture.completedFuture(toReply(response))
                                : CompletableFuture.<GatewayReply>failedFuture(cause);
                    }
                    return CompletableFuture.supplyAsync(() -> attempt + 1,
                                    CompletableFuture.delayedExecutor(retry.delayAfter(attempt), TimeUnit.MILLISECONDS))
                            .thenCompose(next -> send(request, next));
                })
                .thenCompose(reply -> reply);
    }

    private static GatewayReply toReply(HttpResponse<String> response) {
        String text = response.body();
        Map<String, Object> parsed;
        try {
            parsed = text == null || text.isBlank() ? Map.of() : JsonParser.parseObject(text);
        } catch (JsonException e) {
            parsed = Map.of("error", text);
        }
        // GatewayReply's Map.copyOf rejects null values, so JSON nulls are left out here.
        Map<String, Object> body = new LinkedHashMap<>();
        parsed.forEach((key, value) -> {
            if (value != null)
                body.put(key, value);
        });
        return new GatewayReply(response.statusCode(), body);
    }

    private static Throwable unwrap(Throwable error) {
        while (error instanceof CompletionException && error.getCause() != null)
            error = error.getCause();
        return error;
    }
}
