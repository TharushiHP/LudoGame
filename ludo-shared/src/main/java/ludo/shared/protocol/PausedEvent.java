package ludo.shared.protocol;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonObjects;

import java.util.LinkedHashMap;
import java.util.Map;

/** PAUSED event: the game is waiting for {@code colour}, which did not answer in time (Value Object). */
public record PausedEvent(PlayerColor colour, String reason) implements ServerEvent {

    @Override
    public EventType type() {
        return EventType.PAUSED;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("colour", colour.name());
        json.put("reason", reason);
        return json;
    }

    public static PausedEvent fromJson(Map<String, Object> json) {
        return new PausedEvent(JsonObjects.getEnum(json, "colour", PlayerColor.class),
                JsonObjects.getString(json, "reason"));
    }
}
