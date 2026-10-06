package ludo.server;

import ludo.shared.PlayerColor;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.ResumedEvent;
import ludo.shared.snapshot.GameStatus;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A client that never answers: the game pauses after the move timeout, then the server takes
 * over that colour and the game still finishes. Timeouts are shortened so the test is quick.
 */
class ServerTimeoutTest {

    @Test
    void silentPlayerPausesTheGameThenIsSubstituted() throws Exception {
        try (ServerFixture fixture = new ServerFixture(ServerFixture.fastConfig()
                .withMoveTimeoutMs(200).withSubstituteAfterMs(500))) {
            String gameId = fixture.createGame(7);
            // Red, Green, Yellow play; Blue is connected but never answers anything.
            List<FakePlayer> players = fixture.joinFour(gameId, FakePlayer.Mode.AUTO, FakePlayer.Mode.AUTO,
                    FakePlayer.Mode.AUTO, FakePlayer.Mode.SILENT);
            FakePlayer red = players.get(PlayerColor.RED.ordinal());

            PausedEvent paused = red.next(PausedEvent.class, p -> true);
            assertEquals(PlayerColor.BLUE, paused.colour());
            ResumedEvent resumed = red.next(ResumedEvent.class, r -> true);
            assertEquals(PlayerColor.BLUE, resumed.colour());
            assertTrue(resumed.substituted());

            // Substitution is permanent: Blue's client may no longer roll.
            HttpResponse<String> lateRoll = fixture.post("/games/" + gameId + "/roll",
                    "{\"colour\":\"BLUE\",\"turnId\":1,\"expectedVersion\":1,\"requestId\":\"late\"}");
            assertEquals(409, lateRoll.statusCode());
            assertTrue(lateRoll.body().contains("played by the server") || lateRoll.body().contains("is over"),
                    lateRoll.body());

            assertTrue(red.gameOver.await(120, TimeUnit.SECONDS), "the game finishes without Blue's client");
            assertEquals(GameStatus.FINISHED, red.result.status());
            assertTrue(fixture.serverLog().contains("the server plays BLUE"));
        }
    }
}
