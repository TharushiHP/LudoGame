package ludo.server;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonParser;
import ludo.shared.protocol.SnapshotCodec;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The end condition of a remote game (Rule 11 "may continue"): FIRST_WINNER, the server default,
 * stops when the first player finishes; ALL_PLACES plays on until every place is decided.
 */
class ServerEndConditionTest {

    private static final long SEED = 7;

    @Test
    void firstWinnerGameEndsWithOnlyTheWinnerPlaced() throws Exception {
        try (ServerFixture fixture = new ServerFixture(ServerFixture.fastConfig())) {
            String gameId = fixture.createGame(SEED);
            GameSnapshot last = playToTheEnd(fixture, gameId);

            assertEquals(GameStatus.FINISHED, last.status());
            assertEquals(1, placed(last.finishPositions()), "only the winner has a place");
            assertEquals(1, last.finishPositions().get(PlayerColor.RED), "seed 7: Red finishes first");
        }
    }

    @Test
    void allPlacesGamePlacesAllFour() throws Exception {
        try (ServerFixture fixture = new ServerFixture(ServerFixture.fastConfig())) {
            String gameId = fixture.createGame(SEED, "ALL_PLACES");
            GameSnapshot last = playToTheEnd(fixture, gameId);

            assertEquals(GameStatus.FINISHED, last.status());
            assertEquals(4, placed(last.finishPositions()));
        }
    }

    @Test
    void gameSummaryShowsTheEndConditionAndABadValueIsRejected() throws Exception {
        try (ServerFixture fixture = new ServerFixture(ServerFixture.fastConfig())) {
            String defaultGame = fixture.createGame(SEED);
            String allPlaces = fixture.createGame(SEED, "all_places");

            assertEquals("FIRST_WINNER", summary(fixture, defaultGame).get("endCondition"));
            assertEquals("ALL_PLACES", summary(fixture, allPlaces).get("endCondition"));
            assertTrue(fixture.get("/games").body().contains("\"endCondition\""));

            HttpResponse<String> bad = fixture.post("/games", "{\"seed\":7,\"endCondition\":\"NEVER\"}");
            assertEquals(400, bad.statusCode());
        }
    }

    private static GameSnapshot playToTheEnd(ServerFixture fixture, String gameId) throws Exception {
        List<FakePlayer> players = fixture.joinFour(gameId, FakePlayer.Mode.AUTO);
        for (FakePlayer player : players)
            assertTrue(player.gameOver.await(120, TimeUnit.SECONDS), player.colour + " saw no GAME_OVER");
        Map<String, Object> json = JsonParser.parseObject(fixture.get("/games/" + gameId + "/state").body());
        @SuppressWarnings("unchecked")
        GameSnapshot snapshot = SnapshotCodec.fromJson((Map<String, Object>) json.get("snapshot"));
        for (FakePlayer player : players)
            assertEquals(snapshot.finishPositions(), player.result.finishPositions(), "GAME_OVER agrees with the state");
        return snapshot;
    }

    private static long placed(Map<PlayerColor, Integer> finishPositions) {
        return finishPositions.values().stream().filter(place -> place > 0).count();
    }

    private static Map<String, Object> summary(ServerFixture fixture, String gameId) throws Exception {
        HttpResponse<String> response = fixture.get("/games/" + gameId);
        assertEquals(200, response.statusCode());
        return JsonParser.parseObject(response.body());
    }
}
