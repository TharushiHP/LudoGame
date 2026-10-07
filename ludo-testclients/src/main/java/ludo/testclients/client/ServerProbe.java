package ludo.testclients.client;

import ludo.shared.json.JsonObjects;
import ludo.shared.json.JsonParser;
import ludo.shared.protocol.SnapshotCodec;
import ludo.shared.protocol.StateHasher;
import ludo.testclients.report.ServerStats;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Reads a game's published values after a scenario: GET /games/{id} (queue statistics) and
 * GET /games/{id}/state (version, hash, snapshot). These reads are only for checking, so they are
 * not counted with the clients' requests.
 */
public final class ServerProbe {

    /** The final state as GET /state reports it, and the hash this checker computed itself. */
    public record FinalState(long version, String serverHash, String recomputedHash) {

        public boolean hashMatches() {
            return serverHash != null && serverHash.equals(recomputedHash);
        }
    }

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final String base;

    public ServerProbe(String server) {
        String trimmed = server.trim();
        this.base = trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }

    public ServerStats stats(String gameId) throws IOException {
        return ServerStats.fromJson(get("/games/" + gameId));
    }

    public FinalState state(String gameId) throws IOException {
        Map<String, Object> json = get("/games/" + gameId + "/state");
        String serverHash = json.get("hash") == null ? null : JsonObjects.getString(json, "hash");
        String recomputed = json.get("snapshot") == null ? null
                : StateHasher.hash(SnapshotCodec.fromJson(JsonObjects.getObject(json, "snapshot")));
        return new FinalState(JsonObjects.getLong(json, "version"), serverHash, recomputed);
    }

    private Map<String, Object> get(String path) throws IOException {
        HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).timeout(Duration.ofSeconds(10)).GET().build();
        try {
            HttpResponse<String> response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .get(15, TimeUnit.SECONDS);
            if (response.statusCode() != 200)
                throw new IOException("GET " + path + ": HTTP " + response.statusCode() + " " + response.body());
            return JsonParser.parseObject(response.body());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted during GET " + path, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IOException("GET " + path + " failed: " + e, e);
        }
    }
}
