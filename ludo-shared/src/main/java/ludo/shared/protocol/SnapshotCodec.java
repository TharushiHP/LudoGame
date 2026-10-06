package ludo.shared.protocol;

import ludo.shared.Direction;
import ludo.shared.EffectKind;
import ludo.shared.PieceLocation;
import ludo.shared.PlayerColor;
import ludo.shared.json.JsonObjects;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;
import ludo.shared.snapshot.MysterySnapshot;
import ludo.shared.snapshot.PieceSnapshot;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts a {@link GameSnapshot} to and from its JSON form (Mapper). The field order is fixed and
 * colour maps always follow the enum order Red, Green, Yellow, Blue, so the same snapshot always
 * gives the same JSON text: {@link StateHasher} relies on this. Maps are written exactly as they
 * are (a game snapshot always has all four finishPositions; deciderMemo only the colours with a memo),
 * so decoding gives back an equal snapshot.
 */
public final class SnapshotCodec {

    private SnapshotCodec() {}

    public static Map<String, Object> toJson(GameSnapshot s) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("roundNumber", s.roundNumber());
        json.put("turnCount", s.turnCount());
        json.put("currentPlayer", s.currentPlayer() == null ? null : s.currentPlayer().name());
        json.put("lastRoll", s.lastRoll());
        Map<String, Object> mystery = new LinkedHashMap<>();
        mystery.put("cell", s.mystery().cell());
        mystery.put("roundsRemaining", s.mystery().roundsRemaining());
        json.put("mystery", mystery);
        json.put("finishPositions", colourMap(s.finishPositions()));
        json.put("status", s.status().name());
        List<Object> pieces = new ArrayList<>();
        for (PieceSnapshot p : s.pieces())
            pieces.add(pieceToJson(p));
        json.put("pieces", pieces);
        json.put("deciderMemo", colourMap(s.deciderMemo()));
        return json;
    }

    public static GameSnapshot fromJson(Map<String, Object> json) {
        Map<String, Object> mystery = JsonObjects.getObject(json, "mystery");
        Object current = json.get("currentPlayer");
        List<PieceSnapshot> pieces = new ArrayList<>();
        for (Object piece : JsonObjects.getList(json, "pieces"))
            pieces.add(pieceFromJson(JsonObjects.asObject(piece, "piece")));
        return new GameSnapshot(
                JsonObjects.getInt(json, "roundNumber"),
                JsonObjects.getInt(json, "turnCount"),
                current == null ? null : JsonObjects.getEnum(json, "currentPlayer", PlayerColor.class),
                JsonObjects.getInt(json, "lastRoll"),
                new MysterySnapshot(JsonObjects.getInt(mystery, "cell"), JsonObjects.getInt(mystery, "roundsRemaining")),
                colourMapFromJson(JsonObjects.getObject(json, "finishPositions")),
                JsonObjects.getEnum(json, "status", GameStatus.class),
                pieces,
                colourMapFromJson(JsonObjects.getObject(json, "deciderMemo")));
    }

    /** The colours present in {@code source}, always in enum order, whatever kind of map it is. */
    public static Map<String, Object> colourMap(Map<PlayerColor, Integer> source) {
        Map<String, Object> json = new LinkedHashMap<>();
        for (PlayerColor color : PlayerColor.values()) {
            if (source.containsKey(color))
                json.put(color.name(), source.get(color));
        }
        return json;
    }

    public static Map<PlayerColor, Integer> colourMapFromJson(Map<String, Object> json) {
        Map<PlayerColor, Integer> map = new EnumMap<>(PlayerColor.class);
        for (String key : json.keySet()) {
            map.put(JsonObjects.toEnum(key, "colour", PlayerColor.class), JsonObjects.getInt(json, key));
        }
        return map;
    }

    private static Map<String, Object> pieceToJson(PieceSnapshot p) {
        Map<String, Object> json = new LinkedHashMap<>();
        json.put("colour", p.color().name());
        json.put("number", p.number());
        json.put("location", p.location().name());
        json.put("position", p.position());
        json.put("direction", p.direction().name());
        json.put("captureCount", p.captureCount());
        json.put("effect", p.effect().name());
        json.put("effectRoundsLeft", p.effectRoundsLeft());
        json.put("inBlock", p.inBlock());
        return json;
    }

    private static PieceSnapshot pieceFromJson(Map<String, Object> json) {
        return new PieceSnapshot(
                JsonObjects.getEnum(json, "colour", PlayerColor.class),
                JsonObjects.getInt(json, "number"),
                JsonObjects.getEnum(json, "location", PieceLocation.class),
                JsonObjects.getInt(json, "position"),
                JsonObjects.getEnum(json, "direction", Direction.class),
                JsonObjects.getInt(json, "captureCount"),
                JsonObjects.getEnum(json, "effect", EffectKind.class),
                JsonObjects.getInt(json, "effectRoundsLeft"),
                JsonObjects.getBoolean(json, "inBlock"));
    }
}
