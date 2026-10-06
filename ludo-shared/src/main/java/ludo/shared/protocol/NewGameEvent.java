package ludo.shared.protocol;

import ludo.shared.json.JsonObjects;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NEW_GAME event: the server starts the next game of the same session (Value Object). Same seats,
 * same event streams; a new seed. It comes just before the new game's first STATE. The version
 * counter is not reset, so requests from the previous game stay stale (409).
 *
 * @param gameNumber 1 for a session's first game, then 2, 3, ...
 * @param seed       the new game's dice seed
 */
public record NewGameEvent(int gameNumber, long seed) implements ServerEvent {

    @Override
    public EventType type() {
        return EventType.NEW_GAME;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("gameNumber", gameNumber);
        json.put("seed", seed);
        return json;
    }

    public static NewGameEvent fromJson(Map<String, Object> json) {
        return new NewGameEvent(JsonObjects.getInt(json, "gameNumber"), JsonObjects.getLong(json, "seed"));
    }
}
