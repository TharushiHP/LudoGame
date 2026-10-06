package ludo.shared.protocol;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonObjects;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ACK request: {@code colour} has applied STATE {@code version} and computed {@code hash} from it
 * (Value Object). The server compares the hash with its own before the next turn may start.
 */
public record AckCommand(PlayerColor colour, long version, String hash, String requestId) implements ClientRequest {

    @Override
    public RequestType type() {
        return RequestType.ACK;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("colour", colour.name());
        json.put("version", version);
        json.put("hash", hash);
        json.put("requestId", requestId);
        return json;
    }

    public static AckCommand fromJson(Map<String, Object> json) {
        return new AckCommand(JsonObjects.getEnum(json, "colour", PlayerColor.class),
                JsonObjects.getLong(json, "version"), JsonObjects.getString(json, "hash"),
                JsonObjects.getString(json, "requestId"));
    }
}
