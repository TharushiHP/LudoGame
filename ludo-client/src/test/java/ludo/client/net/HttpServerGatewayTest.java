package ludo.client.net;

import com.sun.net.httpserver.HttpExchange;
import ludo.shared.PlayerColor;
import ludo.shared.json.JsonParser;
import ludo.shared.protocol.RollCommand;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/** Retries resend the very same request (same requestId), so the server's idempotency makes them safe. */
class HttpServerGatewayTest {

    private static final RetryPolicy FAST = new RetryPolicy(4, 10, 50);

    private final List<String> requestIds = new CopyOnWriteArrayList<>();
    private final AtomicInteger calls = new AtomicInteger();

    private HttpServerGateway gateway(StubServer server) {
        return new HttpServerGateway(HttpClient.newHttpClient(), server.url(), FAST);
    }

    private void record(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        requestIds.add((String) JsonParser.parseObject(body).get("requestId"));
    }

    private static void reply(HttpExchange exchange, int status, String json) throws IOException {
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Test
    void a503IsRetriedWithTheSameRequestId() throws Exception {
        try (StubServer server = new StubServer("/games/1/roll", exchange -> {
            record(exchange);
            if (calls.incrementAndGet() == 1)
                reply(exchange, 503, "{\"error\":\"busy\"}");
            else
                reply(exchange, 200, "{\"rolled\":4}");
        })) {
            GatewayReply reply = gateway(server).roll("1", new RollCommand(PlayerColor.RED, 1, 2, "req-1"))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(200, reply.status());
            assertEquals(List.of("req-1", "req-1"), requestIds);
        }
    }

    @Test
    void aDroppedConnectionIsRetriedWithTheSameRequestId() throws Exception {
        try (StubServer server = new StubServer("/games/1/roll", exchange -> {
            record(exchange);
            if (calls.incrementAndGet() == 1)
                exchange.close(); // no response at all: the client sees a network error
            else
                reply(exchange, 200, "{}");
        })) {
            GatewayReply reply = gateway(server).roll("1", new RollCommand(PlayerColor.RED, 1, 2, "req-2"))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(200, reply.status());
            assertEquals(2, requestIds.size());
            assertEquals("req-2", requestIds.get(0));
            assertEquals("req-2", requestIds.get(1));
        }
    }

    @Test
    void a409IsReturnedAtOnceAndNotRetried() throws Exception {
        try (StubServer server = new StubServer("/games/1/roll", exchange -> {
            record(exchange);
            reply(exchange, 409, "{\"error\":\"stale\"}");
        })) {
            GatewayReply reply = gateway(server).roll("1", new RollCommand(PlayerColor.RED, 1, 2, "req-3"))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(409, reply.status());
            assertEquals("409 stale", reply.error());
            assertEquals(1, requestIds.size());
        }
    }

    @Test
    void retriesStopAfterTheLastAttempt() throws Exception {
        try (StubServer server = new StubServer("/games/1/roll", exchange -> {
            record(exchange);
            reply(exchange, 503, "{\"error\":\"busy\"}");
        })) {
            GatewayReply reply = gateway(server).roll("1", new RollCommand(PlayerColor.RED, 1, 2, "req-4"))
                    .get(5, TimeUnit.SECONDS);
            assertEquals(503, reply.status());
            assertEquals(FAST.maxAttempts(), requestIds.size());
        }
    }

    @Test
    void gameListIncludesTheTakenColours() throws Exception {
        try (StubServer server = new StubServer("/games", exchange -> reply(exchange, 200,
                "{\"games\":[{\"gameId\":\"3\",\"state\":\"WaitingForPlayers\",\"version\":0,\"joined\":2,"
                        + "\"taken\":[\"RED\",\"BLUE\"],\"seed\":7,\"turnDelayMs\":500}]}"))) {
            List<GameSummary> games = gateway(server).listGames().get(5, TimeUnit.SECONDS);
            assertEquals(1, games.size());
            assertTrue(games.get(0).isTaken(PlayerColor.RED));
            assertTrue(games.get(0).isTaken(PlayerColor.BLUE));
            assertFalse(games.get(0).isTaken(PlayerColor.GREEN));
            assertFalse(games.get(0).endsAtFirstWinner(), "no endCondition field: an older server plays all places");
        }
    }

    @Test
    void gameListReadsTheEndCondition() throws Exception {
        try (StubServer server = new StubServer("/games", exchange -> reply(exchange, 200,
                "{\"games\":[{\"gameId\":\"4\",\"state\":\"WaitingForPlayers\",\"version\":0,\"joined\":0,"
                        + "\"taken\":[],\"seed\":7,\"turnDelayMs\":500,\"endCondition\":\"FIRST_WINNER\"}]}"))) {
            GameSummary game = gateway(server).listGames().get(5, TimeUnit.SECONDS).get(0);
            assertEquals("FIRST_WINNER", game.endCondition());
            assertTrue(game.endsAtFirstWinner());
        }
    }

    @Test
    void backoffDoublesUpToTheCap() {
        RetryPolicy policy = RetryPolicy.standard();
        assertEquals(List.of(250L, 500L, 1000L, 2000L, 2000L),
                List.of(policy.delayAfter(1), policy.delayAfter(2), policy.delayAfter(3), policy.delayAfter(4), policy.delayAfter(5)));
    }
}
