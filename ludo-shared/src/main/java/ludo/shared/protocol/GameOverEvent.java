package ludo.shared.protocol;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonObjects;
import ludo.shared.snapshot.GameStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** GAME_OVER event: how the game ended and each colour's finishing place, 0 = not ranked (Value Object). */
public record GameOverEvent(GameStatus status, Map<PlayerColor, Integer> finishPositions) implements ServerEvent {

    public GameOverEvent {
        Map<PlayerColor, Integer> copy = new EnumMap<>(PlayerColor.class);
        copy.putAll(finishPositions);
        finishPositions = Collections.unmodifiableMap(copy);
    }

    @Override
    public EventType type() {
        return EventType.GAME_OVER;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("status", status.name());
        json.put("finishPositions", SnapshotCodec.colourMap(finishPositions));
        return json;
    }

    public static GameOverEvent fromJson(Map<String, Object> json) {
        return new GameOverEvent(JsonObjects.getEnum(json, "status", GameStatus.class),
                SnapshotCodec.colourMapFromJson(JsonObjects.getObject(json, "finishPositions")));
    }
}
