package ludo.server;

import ludo.shared.protocol.DecisionRequest;
import ludo.shared.protocol.RollRequest;
import ludo.shared.snapshot.GameStatus;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Stopping the server (what the shutdown hook does) interrupts each game thread: the game ends as
 * ABORTED, every client is told so with GAME_OVER, and every server thread of the game ends.
 */
class ServerShutdownTest {

    @Test
    void stopWhileWaitingForARollAbortsTheGame() throws Exception {
        ServerFixture fixture = new ServerFixture(ServerFixture.fastConfig());
        List<FakePlayer> players = fixture.joinFour(fixture.createGame(7), FakePlayer.Mode.ACK_ONLY);
        players.get(0).next(RollRequest.class, r -> true);

        fixture.close(); // also asserts that the game thread has ended

        assertAllToldAborted(players);
        assertTrue(fixture.serverLog().contains("game 1 over: ABORTED"));
    }

    @Test
    void stopWhileWaitingForADecisionAbortsTheGame() throws Exception {
        ServerFixture fixture = new ServerFixture(ServerFixture.fastConfig());
        List<FakePlayer> players = fixture.joinFour(fixture.createGame(7), FakePlayer.Mode.ACK_ONLY);
        RollRequest request = players.get(0).next(RollRequest.class, r -> true);
        FakePlayer roller = players.get(request.colour().ordinal());
        roller.roll(request.turnId(), request.version(), FakePlayer.newId());
        roller.next(DecisionRequest.class, q -> true);

        fixture.close();

        assertAllToldAborted(players);
        assertTrue(fixture.serverLog().contains("answered locally, the game stops after this roll"));
    }

    private static void assertAllToldAborted(List<FakePlayer> players) throws InterruptedException {
        for (FakePlayer player : players) {
            assertTrue(player.gameOver.await(5, TimeUnit.SECONDS), player.colour + " got no GAME_OVER");
            assertEquals(GameStatus.ABORTED, player.result.status());
        }
    }
}
