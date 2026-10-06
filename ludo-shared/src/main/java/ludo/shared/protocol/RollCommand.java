package ludo.shared.protocol;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonObjects;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ROLL request: {@code colour} asks the server to roll for the turn named in the ROLL_REQUEST
 * (Value Object). {@code expectedVersion} detects stale requests; {@code requestId} makes retries safe.
 */
public record RollCommand(PlayerColor colour, long turnId, long expectedVersion, String requestId)
        implements ClientRequest {

    @Override
    public RequestType type() {
        return RequestType.ROLL;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("colour", colour.name());
        json.put("turnId", turnId);
        json.put("expectedVersion", expectedVersion);
        json.put("requestId", requestId);
        return json;
    }

    public static RollCommand fromJson(Map<String, Object> json) {
        return new RollCommand(JsonObjects.getEnum(json, "colour", PlayerColor.class),
                JsonObjects.getLong(json, "turnId"), JsonObjects.getLong(json, "expectedVersion"),
                JsonObjects.getString(json, "requestId"));
    }
}
