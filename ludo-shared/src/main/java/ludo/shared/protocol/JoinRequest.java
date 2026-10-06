package ludo.shared.protocol;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonObjects;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JOIN request: a client takes {@code colour} in a game (Value Object). It declares its Rule 7
 * behaviour, which the server needs before the game starts (Blue declares false).
 */
public record JoinRequest(PlayerColor colour, String clientName, boolean triesOtherPiecesWhenBlocked)
        implements ClientRequest {

    @Override
    public RequestType type() {
        return RequestType.JOIN;
    }

    @Override
    public Map<String, Object> toJson() {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("colour", colour.name());
        json.put("clientName", clientName);
        json.put("triesOtherPiecesWhenBlocked", triesOtherPiecesWhenBlocked);
        return json;
    }

    public static JoinRequest fromJson(Map<String, Object> json) {
        return new JoinRequest(JsonObjects.getEnum(json, "colour", PlayerColor.class),
                JsonObjects.getString(json, "clientName"),
                JsonObjects.getBoolean(json, "triesOtherPiecesWhenBlocked"));
    }
}
