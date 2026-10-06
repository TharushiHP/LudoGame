package ludo.server;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonParser;
import ludo.shared.protocol.DecisionRequest;
import ludo.shared.protocol.RollRequest;
import ludo.shared.protocol.StateEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Request validation over real HTTP. The fake clients only ACK; each test sends the ROLL and
 * DECISION requests itself, so it can send wrong, stale and repeated ones on purpose.
 */
class ServerProtocolTest {

    private ServerFixture fixture;
    private String gameId;
    private List<FakePlayer> players;

    @BeforeEach
    void startGame() throws Exception {
        fixture = new ServerFixture(ServerFixture.fastConfig());
        gameId = fixture.createGame(7);
        players = fixture.joinFour(gameId, FakePlayer.Mode.ACK_ONLY);
    }

    @AfterEach
    void stopServer() {
        fixture.close();
    }

    @Test
    void rollFromTheWrongColourIsRejectedWith409() throws Exception {
        RollRequest request = firstRollRequest();
        FakePlayer other = otherThan(request.colour());

        HttpResponse<String> response = other.roll(request.turnId(), request.version(), FakePlayer.newId());

        assertEquals(409, response.statusCode());
        assertTrue(response.body().contains("waiting for " + request.colour() + " to roll"), response.body());
    }

    @Test
    void staleVersionDecisionIsRejectedWith409() throws Exception {
        RollRequest request = firstRollRequest();
        FakePlayer roller = player(request.colour());
        assertEquals(200, roller.roll(request.turnId(), request.version(), FakePlayer.newId()).statusCode());
        DecisionRequest question = roller.next(DecisionRequest.class, q -> q.colour() == roller.colour);

        HttpResponse<String> stale = roller.decide(question, question.version() - 1, FakePlayer.newId());

        assertEquals(409, stale.statusCode());
        assertTrue(stale.body().contains("stale version"), stale.body());
        // The game is still waiting for that decision: the correct version is accepted.
        assertEquals(200, roller.decide(question, question.version(), FakePlayer.newId()).statusCode());
    }

    @Test
    void repeatedRequestIdGetsTheStoredReplyAndIsNotAppliedTwice() throws Exception {
        RollRequest request = firstRollRequest();
        FakePlayer roller = player(request.colour());

        HttpResponse<String> first = roller.roll(request.turnId(), request.version(), "roll-once");
        HttpResponse<String> retry = roller.roll(request.turnId(), request.version(), "roll-once");
        HttpResponse<String> fresh = roller.roll(request.turnId(), request.version(), FakePlayer.newId());

        assertEquals(200, first.statusCode());
        assertEquals(200, retry.statusCode(), "a retry gets the original reply");
        assertEquals(first.body(), retry.body());
        assertEquals(409, fresh.statusCode(), "the same ROLL with a new requestId is a second roll: rejected");

        DecisionRequest question = roller.next(DecisionRequest.class, q -> q.colour() == roller.colour);
        HttpResponse<String> decided = roller.decide(question, question.version(), "decide-once");
        HttpResponse<String> decidedAgain = roller.decide(question, question.version(), "decide-once");
        assertEquals(200, decided.statusCode());
        assertEquals(decided.body(), decidedAgain.body());

        // Exactly one roll was applied: the next STATE's log has one "rolled" line for that turn.
        StateEvent state = roller.next(StateEvent.class, s -> s.version() > request.version());
        long rolls = state.log().stream().filter(line -> line.contains(" player rolled ")).count();
        assertEquals(1, rolls, state.log().toString());
        assertTrue(fixture.serverLog().contains("duplicate requestId"), "the server log records the duplicate");
    }

    @Test
    void ackWithAWrongHashGets409AndTheStateIsSentAgain() throws Exception {
        RollRequest request = firstRollRequest();
        FakePlayer roller = player(request.colour());
        roller.roll(request.turnId(), request.version(), FakePlayer.newId());
        DecisionRequest question = roller.next(DecisionRequest.class, q -> q.colour() == roller.colour);
        roller.decide(question, question.version(), FakePlayer.newId());
        FakePlayer watcher = otherThan(request.colour());
        StateEvent state = watcher.next(StateEvent.class, s -> s.version() > request.version());

        HttpResponse<String> bad = watcher.ack(state.version(), "0".repeat(64));

        assertEquals(409, bad.statusCode());
        assertTrue(bad.body().contains("hash mismatch"), bad.body());
        StateEvent resent = watcher.next(StateEvent.class, s -> s.version() == state.version());
        assertEquals(state.hash(), resent.hash());
        assertTrue(fixture.serverLog().contains("hash mismatch from " + watcher.colour));
    }

    @Test
    void joinAfterTheGameStartedIsRejected() throws Exception {
        firstRollRequest();
        HttpResponse<String> response = fixture.post("/games/" + gameId + "/join",
                "{\"colour\":\"RED\",\"clientName\":\"late\",\"triesOtherPiecesWhenBlocked\":true}");
        assertEquals(409, response.statusCode());
    }

    @Test
    void malformedJsonGets400() throws Exception {
        assertEquals(400, fixture.post("/games/" + gameId + "/roll", "{\"colour\":").statusCode());
        assertEquals(400, fixture.post("/games/" + gameId + "/roll", "{\"colour\":\"RED\"}").statusCode(), "missing fields");
        assertEquals(400, fixture.post("/games/" + gameId + "/ack",
                "{\"colour\":\"PINK\",\"version\":1,\"hash\":\"x\",\"requestId\":\"r\"}").statusCode(), "unknown colour");
    }

    @Test
    void unknownGameAndPathGet404() throws Exception {
        assertEquals(404, fixture.post("/games/999/roll", "{}").statusCode());
        assertEquals(404, fixture.get("/games/999/state").statusCode());
        assertEquals(404, fixture.get("/nothing-here").statusCode());
        assertEquals(405, fixture.get("/games/" + gameId + "/roll").statusCode());
    }

    @Test
    void healthAndGameListAnswer() throws Exception {
        HttpResponse<String> health = fixture.get("/health");
        assertEquals(200, health.statusCode());
        assertEquals("UP", JsonParser.parseObject(health.body()).get("status"));
        firstRollRequest();
        HttpResponse<String> games = fixture.get("/games");
        assertEquals(200, games.statusCode());
        assertTrue(games.body().contains("AwaitingRoll"), games.body());
        assertTrue(games.body().contains("\"taken\":[\"RED\",\"GREEN\",\"YELLOW\",\"BLUE\"]"), games.body());
    }

    // --- helpers ---

    private RollRequest firstRollRequest() throws InterruptedException {
        return players.get(0).next(RollRequest.class, r -> true);
    }

    private FakePlayer player(PlayerColor colour) {
        return players.get(colour.ordinal());
    }

    private FakePlayer otherThan(PlayerColor colour) {
        return players.get((colour.ordinal() + 1) % players.size());
    }
}
