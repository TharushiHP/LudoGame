package ludo.shared.protocol;

import ludo.shared.Direction;
import ludo.shared.EffectKind;
import ludo.shared.PieceLocation;
import ludo.shared.PlayerColor;
import ludo.shared.json.JsonException;
import ludo.shared.json.JsonParser;
import ludo.shared.json.JsonWriter;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;
import ludo.shared.snapshot.MysterySnapshot;
import ludo.shared.snapshot.PieceSnapshot;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;

import static org.junit.jupiter.api.Assertions.*;

/** Snapshot codec, state hash and protocol message round trips. */
class ProtocolTest {

    /** A small fixed snapshot: two pieces, one memo, an active mystery cell. */
    static GameSnapshot sample() {
        List<PieceSnapshot> pieces = List.of(
                new PieceSnapshot(PlayerColor.YELLOW, 1, PieceLocation.MAIN_PATH, 14, Direction.CLOCKWISE,
                        1, EffectKind.ENERGIZED, 2, true),
                new PieceSnapshot(PlayerColor.BLUE, 3, PieceLocation.BASE, -1, Direction.COUNTER_CLOCKWISE,
                        0, EffectKind.NONE, 0, false));
        return new GameSnapshot(5, 19, PlayerColor.BLUE, 6, new MysterySnapshot(30, 2),
                finishPositions(), GameStatus.IN_PROGRESS, pieces, Map.of(PlayerColor.BLUE, 2));
    }

    /** Like Game.snapshot(): all four colours, built in the game's player order Yellow, Blue, Red, Green. */
    private static Map<PlayerColor, Integer> finishPositions() {
        Map<PlayerColor, Integer> finish = new LinkedHashMap<>();
        finish.put(PlayerColor.YELLOW, 0);
        finish.put(PlayerColor.BLUE, 3);
        finish.put(PlayerColor.RED, 0);
        finish.put(PlayerColor.GREEN, 1);
        return finish;
    }

    private static final String SAMPLE_JSON = "{\"roundNumber\":5,\"turnCount\":19,\"currentPlayer\":\"BLUE\","
            + "\"lastRoll\":6,\"mystery\":{\"cell\":30,\"roundsRemaining\":2},"
            + "\"finishPositions\":{\"RED\":0,\"GREEN\":1,\"YELLOW\":0,\"BLUE\":3},\"status\":\"IN_PROGRESS\","
            + "\"pieces\":[{\"colour\":\"YELLOW\",\"number\":1,\"location\":\"MAIN_PATH\",\"position\":14,"
            + "\"direction\":\"CLOCKWISE\",\"captureCount\":1,\"effect\":\"ENERGIZED\",\"effectRoundsLeft\":2,"
            + "\"inBlock\":true},{\"colour\":\"BLUE\",\"number\":3,\"location\":\"BASE\",\"position\":-1,"
            + "\"direction\":\"COUNTER_CLOCKWISE\",\"captureCount\":0,\"effect\":\"NONE\",\"effectRoundsLeft\":0,"
            + "\"inBlock\":false}],\"deciderMemo\":{\"BLUE\":2}}";

    // Snapshot codec

    @Test
    void snapshotHasAFixedCanonicalJsonForm() {
        assertEquals(SAMPLE_JSON, JsonWriter.write(SnapshotCodec.toJson(sample())));
    }

    @Test
    void snapshotSurvivesAJsonRoundTrip() {
        GameSnapshot original = sample();
        String text = JsonWriter.write(SnapshotCodec.toJson(original));
        assertEquals(original, SnapshotCodec.fromJson(JsonParser.parseObject(text)));
    }

    @Test
    void snapshotWithoutCurrentPlayerRoundTrips() {
        GameSnapshot s = new GameSnapshot(0, 0, null, 0, new MysterySnapshot(-1, 0), Map.of(),
                GameStatus.NOT_STARTED, List.of(), Map.of());
        GameSnapshot back = SnapshotCodec.fromJson(JsonParser.parseObject(JsonWriter.write(SnapshotCodec.toJson(s))));
        assertNull(back.currentPlayer());
        assertEquals(s, back);
    }

    @Test
    void codecRejectsUnknownEnumValues() {
        String bad = SAMPLE_JSON.replace("\"IN_PROGRESS\"", "\"WINNING\"");
        assertThrows(JsonException.class, () -> SnapshotCodec.fromJson(JsonParser.parseObject(bad)));
    }

    // State hash

    @Test
    void hashIsSha256OfTheCanonicalJsonAsLowercaseHex() {
        String hash = StateHasher.hash(sample());
        assertEquals(StateHasher.sha256Hex(SAMPLE_JSON), hash);
        assertTrue(hash.matches("[0-9a-f]{64}"), hash);
        // Well-known SHA-256 test vector, so the hex encoding itself is checked too.
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", StateHasher.sha256Hex("abc"));
    }

    @Test
    void equalSnapshotsHaveEqualHashesWhateverMapTheyWereBuiltFrom() {
        GameSnapshot a = sample();
        // HashMap iteration order is unspecified; the hash must not depend on it.
        GameSnapshot b = new GameSnapshot(a.roundNumber(), a.turnCount(), a.currentPlayer(), a.lastRoll(),
                a.mystery(), new HashMap<>(a.finishPositions()), a.status(), new ArrayList<>(a.pieces()),
                new HashMap<>(a.deciderMemo()));
        assertEquals(a, b);
        assertEquals(StateHasher.hash(a), StateHasher.hash(b));
    }

    @Test
    void anyChangedFieldChangesTheHash() {
        GameSnapshot a = sample();
        GameSnapshot b = new GameSnapshot(a.roundNumber(), a.turnCount(), a.currentPlayer(), 5,
                a.mystery(), a.finishPositions(), a.status(), a.pieces(), a.deciderMemo());
        assertNotEquals(StateHasher.hash(a), StateHasher.hash(b));
    }

    // Protocol messages

    @Test
    void everyServerEventRoundTrips() {
        List<ServerEvent> events = List.of(
                new StateEvent(12, sample(), StateHasher.hash(sample()), List.of("\nRed player rolled 6.", "line 2")),
                new RollRequest(PlayerColor.RED, 40, 12),
                new DecisionRequest(PlayerColor.BLUE, 7, DecisionKind.CHOOSE_PIECE, 4, List.of(1, 3), 12, sample()),
                new PausedEvent(PlayerColor.GREEN, "no ROLL within 10000 ms"),
                new ResumedEvent(PlayerColor.GREEN, true),
                new GameOverEvent(GameStatus.FINISHED, Map.of(PlayerColor.RED, 1, PlayerColor.BLUE, 2)));
        for (ServerEvent event : events) {
            String text = JsonWriter.write(event.toJson());
            assertEquals(event, ServerEvent.fromJson(event.type(), JsonParser.parseObject(text)), text);
        }
    }

    @Test
    void everyClientRequestRoundTrips() {
        assertRoundTrip(new JoinRequest(PlayerColor.BLUE, "blue-pc", false), JoinRequest::fromJson);
        assertRoundTrip(new RollCommand(PlayerColor.RED, 3, 9, "r-1"), RollCommand::fromJson);
        assertRoundTrip(DecisionReply.choosePiece(PlayerColor.BLUE, 5, 9, "d-1", OptionalInt.of(2), OptionalInt.of(3)),
                DecisionReply::fromJson);
        assertRoundTrip(DecisionReply.choosePiece(PlayerColor.RED, 6, 9, "d-2", OptionalInt.empty(), OptionalInt.empty()),
                DecisionReply::fromJson);
        assertRoundTrip(DecisionReply.moveFromBase(PlayerColor.GREEN, 7, 9, "d-3", true), DecisionReply::fromJson);
        assertRoundTrip(new AckCommand(PlayerColor.YELLOW, 9, "abc", "a-1"), AckCommand::fromJson);
    }

    @Test
    void requestWithAMissingFieldIsRejected() {
        assertThrows(JsonException.class, () -> RollCommand.fromJson(JsonParser.parseObject(
                "{\"colour\":\"RED\",\"turnId\":1,\"expectedVersion\":2}")));
        assertThrows(JsonException.class, () -> JoinRequest.fromJson(JsonParser.parseObject(
                "{\"colour\":\"PURPLE\",\"clientName\":\"x\",\"triesOtherPiecesWhenBlocked\":true}")));
    }

    private static <T extends ClientRequest> void assertRoundTrip(T request,
                                                               java.util.function.Function<Map<String, Object>, T> parser) {
        String text = JsonWriter.write(request.toJson());
        assertEquals(request, parser.apply(JsonParser.parseObject(text)), text);
    }
}
