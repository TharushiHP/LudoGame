package ludo.shared.protocol;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonObjects;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * DECISION request: a client's answer to one DECISION_REQUEST (Value Object).
 * For CHOOSE_PIECE it fills {@code piece} (empty = nothing to choose) and optionally {@code memo};
 * for MOVE_FROM_BASE it fills {@code fromBase}.
 */
public record DecisionReply(PlayerColor colour, long decisionId, long expectedVersion, String requestId,
                            OptionalInt piece, OptionalInt memo, Optional<Boolean> fromBase)
        implements ClientRequest {

    public static DecisionReply choosePiece(PlayerColor colour, long decisionId, long expectedVersion,
                                            String requestId, OptionalInt piece, OptionalInt memo) {
        return new DecisionReply(colour, decisionId, expectedVersion, requestId, piece, memo, Optional.empty());
    }

    public static DecisionReply moveFromBase(PlayerColor colour, long decisionId, long expectedVersion,
                                             String requestId, boolean fromBase) {
        return new DecisionReply(colour, decisionId, expectedVersion, requestId,
                OptionalInt.empty(), OptionalInt.empty(), Optional.of(fromBase));
    }

    @Override
    public RequestType type() {
        return RequestType.DECISION;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("colour", colour.name());
        json.put("decisionId", decisionId);
        json.put("expectedVersion", expectedVersion);
        json.put("requestId", requestId);
        json.put("piece", piece.isPresent() ? piece.getAsInt() : null);
        json.put("memo", memo.isPresent() ? memo.getAsInt() : null);
        json.put("fromBase", fromBase.orElse(null));
        return json;
    }

    public static DecisionReply fromJson(Map<String, Object> json) {
        Optional<Boolean> fromBase = json.get("fromBase") == null
                ? Optional.empty() : Optional.of(JsonObjects.getBoolean(json, "fromBase"));
        return new DecisionReply(JsonObjects.getEnum(json, "colour", PlayerColor.class),
                JsonObjects.getLong(json, "decisionId"), JsonObjects.getLong(json, "expectedVersion"),
                JsonObjects.getString(json, "requestId"),
                JsonObjects.getOptionalInt(json, "piece"), JsonObjects.getOptionalInt(json, "memo"), fromBase);
    }
}
