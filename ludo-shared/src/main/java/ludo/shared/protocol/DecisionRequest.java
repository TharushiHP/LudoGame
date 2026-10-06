package ludo.shared.protocol;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonException;
import ludo.shared.json.JsonObjects;
import ludo.shared.snapshot.GameSnapshot;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DECISION_REQUEST event: {@code colour} must answer this question with a DECISION quoting
 * {@code decisionId} (Value Object). It carries the snapshot the game asked about: it is taken
 * after the dice roll, so it is newer than the last STATE. {@code candidates} are the piece numbers
 * the player may choose (CHOOSE_PIECE only; empty for MOVE_FROM_BASE).
 */
public record DecisionRequest(PlayerColor colour, long decisionId, DecisionKind kind, int roll,
                              List<Integer> candidates, long version, GameSnapshot snapshot) implements ServerEvent {

    public DecisionRequest {
        candidates = List.copyOf(candidates);
    }

    @Override
    public EventType type() {
        return EventType.DECISION_REQUEST;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("colour", colour.name());
        json.put("decisionId", decisionId);
        json.put("kind", kind.name());
        json.put("roll", roll);
        json.put("candidates", new ArrayList<Object>(candidates));
        json.put("version", version);
        json.put("snapshot", SnapshotCodec.toJson(snapshot));
        return json;
    }

    public static DecisionRequest fromJson(Map<String, Object> json) {
        List<Integer> candidates = new ArrayList<>();
        for (Object value : JsonObjects.getList(json, "candidates")) {
            if (!(value instanceof Long number))
                throw new JsonException("field \"candidates\" must hold whole numbers");
            candidates.add(number.intValue());
        }
        return new DecisionRequest(JsonObjects.getEnum(json, "colour", PlayerColor.class),
                JsonObjects.getLong(json, "decisionId"),
                JsonObjects.getEnum(json, "kind", DecisionKind.class),
                JsonObjects.getInt(json, "roll"), candidates,
                JsonObjects.getLong(json, "version"),
                SnapshotCodec.fromJson(JsonObjects.getObject(json, "snapshot")));
    }
}
