package ludo.testclients.report;

import ludo.shared.json.JsonObjects;

import java.util.Map;

/**
 * The queue statistics of one game, as GET /games/{id} reports them (Value Object).
 * {@code peakQueueDepth} above 1 proves that requests waited in the game's queue.
 */
public record ServerStats(String gameId, String state, int queueCapacity, int peakQueueDepth,
                          long accepted, long rejected, long refused, long otherErrors) {

    public static ServerStats fromJson(Map<String, Object> json) {
        return new ServerStats(JsonObjects.getString(json, "gameId"), JsonObjects.getString(json, "state"),
                JsonObjects.getInt(json, "queueCapacity"), JsonObjects.getInt(json, "peakQueueDepth"),
                JsonObjects.getLong(json, "accepted"), JsonObjects.getLong(json, "rejected"),
                JsonObjects.getLong(json, "refused"), JsonObjects.getLong(json, "otherErrors"));
    }
}
