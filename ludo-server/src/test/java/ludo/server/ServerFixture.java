package ludo.server;

import ludo.server.config.ServerConfig;
import ludo.server.coordinator.GameSession;
import ludo.shared.PlayerColor;
import ludo.shared.json.JsonParser;
import ludo.shared.json.JsonWriter;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Starts a real server in-process on a free port, with a quiet log captured in memory, and gives
 * tests a small HTTP client for it. {@link #close} stops the server and checks that every game
 * thread really ended, so no server thread is left running (and printing) during later tests
 * such as the golden master, which captures System.out.
 */
final class ServerFixture implements AutoCloseable {

    final LudoServer server;
    final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    final String base;
    private final ByteArrayOutputStream logBytes = new ByteArrayOutputStream();
    private final List<FakePlayer> players = new ArrayList<>();

    ServerFixture(ServerConfig config) throws Exception {
        PrintStream out = new PrintStream(logBytes, true, StandardCharsets.UTF_8);
        server = LudoServer.start(config.withPort(0).withEchoGameLog(false).withOut(out));
        base = "http://localhost:" + server.port();
    }

    /** Turn delay 0 so a whole game takes seconds; generous timeouts so a slow machine is not "absent". */
    static ServerConfig fastConfig() {
        return ServerConfig.defaults().withTurnDelayMs(0).withMoveTimeoutMs(5_000).withSubstituteAfterMs(30_000);
    }

    String createGame(long seed) throws Exception {
        return createGame("{\"seed\":" + seed + "}");
    }

    /** A game with an explicit end condition ("FIRST_WINNER" or "ALL_PLACES"). */
    String createGame(long seed, String endCondition) throws Exception {
        return createGame("{\"seed\":" + seed + ",\"endCondition\":\"" + endCondition + "\"}");
    }

    private String createGame(String body) throws Exception {
        HttpResponse<String> response = post("/games", body);
        if (response.statusCode() != 201)
            throw new IllegalStateException("POST /games failed: " + response.statusCode() + " " + response.body());
        return (String) JsonParser.parseObject(response.body()).get("gameId");
    }

    /** One fake client per colour, events connected and joined, in Red, Green, Yellow, Blue order. */
    List<FakePlayer> joinFour(String gameId, FakePlayer.Mode... modes) throws Exception {
        List<FakePlayer> four = new ArrayList<>();
        for (PlayerColor colour : PlayerColor.values()) {
            FakePlayer.Mode mode = modes.length == 1 ? modes[0] : modes[colour.ordinal()];
            FakePlayer player = new FakePlayer(this, gameId, colour, mode);
            players.add(player);
            player.openEvents();
            four.add(player);
        }
        for (FakePlayer player : four)
            player.join();
        return four;
    }

    HttpResponse<String> post(String path, String body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    HttpResponse<String> post(String path, Map<String, Object> json) throws Exception {
        return post(path, JsonWriter.write(json));
    }

    HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    String serverLog() {
        return logBytes.toString(StandardCharsets.UTF_8);
    }

    @Override
    public void close() {
        server.stop();
        players.forEach(FakePlayer::stop);
        for (GameSession session : server.registry().all())
            assertFalse(session.gameThread().isAlive(), "game thread " + session.gameThread().getName() + " still running");
    }
}
