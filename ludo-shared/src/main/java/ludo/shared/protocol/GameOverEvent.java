package ludo.shared.protocol;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonObjects;
import ludo.shared.snapshot.GameStatus;

import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * GAME_OVER event: how the game ended and each colour's finishing place, 0 = not ranked (Value Object).
 * {@code nextGameInMs} &gt; 0 means the server starts the next game in the same session after that
 * many milliseconds (a NEW_GAME event follows); 0 means this was the last game and the event stream
 * ends. A GAME_OVER without the field (an older server) reads as 0.
 */
public record GameOverEvent(GameStatus status, Map<PlayerColor, Integer> finishPositions, long nextGameInMs)
        implements ServerEvent {

    public GameOverEvent {
        Map<PlayerColor, Integer> copy = new EnumMap<>(PlayerColor.class);
        copy.putAll(finishPositions);
        finishPositions = Collections.unmodifiableMap(copy);
    }

    /** The last game of its session: no next game follows. */
    public GameOverEvent(GameStatus status, Map<PlayerColor, Integer> finishPositions) {
        this(status, finishPositions, 0);
    }

    /** True when the server will start another game in the same session. */
    public boolean hasNextGame() {
        return nextGameInMs > 0;
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
        json.put("nextGameInMs", nextGameInMs);
        return json;
    }

    public static GameOverEvent fromJson(Map<String, Object> json) {
        long next = json.get("nextGameInMs") == null ? 0 : JsonObjects.getLong(json, "nextGameInMs");
        return new GameOverEvent(JsonObjects.getEnum(json, "status", GameStatus.class),
                SnapshotCodec.colourMapFromJson(JsonObjects.getObject(json, "finishPositions")), next);
    }
}
