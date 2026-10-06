package ludo.shared.protocol;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonObjects;

import java.util.LinkedHashMap;
import java.util.Map;

/** ROLL_REQUEST event: {@code colour} must now send ROLL quoting this turnId and version (Value Object). */
public record RollRequest(PlayerColor colour, long turnId, long version) implements ServerEvent {

    @Override
    public EventType type() {
        return EventType.ROLL_REQUEST;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("colour", colour.name());
        json.put("turnId", turnId);
        json.put("version", version);
        return json;
    }

    public static RollRequest fromJson(Map<String, Object> json) {
        return new RollRequest(JsonObjects.getEnum(json, "colour", PlayerColor.class),
                JsonObjects.getLong(json, "turnId"), JsonObjects.getLong(json, "version"));
    }
}
