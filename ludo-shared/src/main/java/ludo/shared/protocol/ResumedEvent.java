package ludo.shared.protocol;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonObjects;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RESUMED event: the game continues after a pause (Value Object). {@code substituted} is true when
 * the client never answered and the server now plays {@code colour} itself for the rest of the game.
 */
public record ResumedEvent(PlayerColor colour, boolean substituted) implements ServerEvent {

    @Override
    public EventType type() {
        return EventType.RESUMED;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("colour", colour.name());
        json.put("substituted", substituted);
        return json;
    }

    public static ResumedEvent fromJson(Map<String, Object> json) {
        return new ResumedEvent(JsonObjects.getEnum(json, "colour", PlayerColor.class),
                JsonObjects.getBoolean(json, "substituted"));
    }
}
