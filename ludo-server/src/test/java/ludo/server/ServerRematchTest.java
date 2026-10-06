package ludo.server;

import ludo.shared.PlayerColor;
import ludo.shared.json.JsonParser;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.protocol.NewGameEvent;
import ludo.shared.protocol.PausedEvent;
import ludo.shared.protocol.RollRequest;
import ludo.shared.protocol.ServerEvent;
import ludo.shared.protocol.StateEvent;
import ludo.shared.snapshot.GameStatus;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The automatic next game (--rematch-delay): after a GAME_OVER that announces it, the same game
 * thread starts a new game in the same session, with the same seats and event streams, a new
 * seed, and a version that keeps counting up. Every other server test runs with rematch off.
 */
class ServerRematchTest {

    private static final long SEED = 7;
    private static final long REMATCH_MS = 100;

    @Test
    void fourPlayersPlayTwoGamesInOneSessionWithoutRejoining() throws Exception {
        try (ServerFixture fixture = new ServerFixture(ServerFixture.fastConfig().withRematchDelayMs(REMATCH_MS))) {
            String gameId = fixture.createGame(SEED);
            List<FakePlayer> players = fixture.joinFour(gameId, FakePlayer.Mode.AUTO);
            FakePlayer red = players.get(PlayerColor.RED.ordinal());

            // A ROLL from game 1, sent again once game 2 runs, is stale: 409.
            RollRequest firstRoll = red.next(RollRequest.class, r -> r.colour() == PlayerColor.RED);
            awaitFirst(red, NewGameEvent.class);
            HttpResponse<String> stale = red.roll(firstRoll.turnId(), firstRoll.version(), FakePlayer.newId());
            assertEquals(409, stale.statusCode(), stale.body());

            for (FakePlayer player : players) {
                assertTrue(player.awaitGameOvers(2, 120_000), player.colour + " did not see two games end");
                List<ServerEvent> seen = snapshotOf(player.seen);
                int over1 = indexOf(seen, GameOverEvent.class, 0);
                int next = indexOf(seen, NewGameEvent.class, over1);
                int over2 = indexOf(seen, GameOverEvent.class, next);
                assertTrue(over1 >= 0 && next > over1 && over2 > next, player.colour + ": GAME_OVER, NEW_GAME, GAME_OVER");

                GameOverEvent first = (GameOverEvent) seen.get(over1);
                assertEquals(GameStatus.FINISHED, first.status());
                assertEquals(REMATCH_MS, first.nextGameInMs(), "the first GAME_OVER announces the next game");
                NewGameEvent newGame = (NewGameEvent) seen.get(next);
                assertEquals(2, newGame.gameNumber());
                assertNotEquals(SEED, newGame.seed(), "the next game has a new seed");
                assertTrue(seen.get(next + 1) instanceof StateEvent, "NEW_GAME comes just before the new game's first STATE");
                assertEquals(GameStatus.FINISHED, ((GameOverEvent) seen.get(over2)).status());

                long previous = 0;
                for (ServerEvent event : seen)
                    if (event instanceof StateEvent state) {
                        assertTrue(state.version() > previous, "versions never reset: v" + state.version() + " after v" + previous);
                        previous = state.version();
                    }
                assertEquals(List.of(), player.problems, "every ROLL, DECISION and ACK succeeds and every hash matches");
                assertTrue(player.ackStatuses.stream().allMatch(status -> status == 200));
            }

            Map<String, Object> summary = JsonParser.parseObject(fixture.get("/games/" + gameId).body());
            assertTrue((Long) summary.get("gameNumber") >= 2, summary.toString());
            assertNotNull(summary.get("seed"));
            assertTrue(fixture.serverLog().contains("[game-" + gameId + "]") && fixture.serverLog().contains("#2 starts with seed"),
                    "the next game is started by the same game thread");
        }
    }

    @Test
    void aSubstitutedColourStaysComputerPlayedInTheNextGame() throws Exception {
        try (ServerFixture fixture = new ServerFixture(ServerFixture.fastConfig()
                .withMoveTimeoutMs(200).withSubstituteAfterMs(500).withRematchDelayMs(REMATCH_MS))) {
            String gameId = fixture.createGame(SEED);
            List<FakePlayer> players = fixture.joinFour(gameId, FakePlayer.Mode.AUTO, FakePlayer.Mode.AUTO,
                    FakePlayer.Mode.AUTO, FakePlayer.Mode.SILENT);
            FakePlayer red = players.get(PlayerColor.RED.ordinal());

            assertTrue(red.awaitGameOvers(2, 120_000), "two games finish although Blue never answers");
            List<ServerEvent> seen = snapshotOf(red.seen);
            int next = indexOf(seen, NewGameEvent.class, 0);
            int over2 = indexOf(seen, GameOverEvent.class, next);
            for (ServerEvent event : seen.subList(next, over2)) {
                assertFalse(event instanceof RollRequest roll && roll.colour() == PlayerColor.BLUE,
                        "the server rolls for Blue in game 2 without asking");
                assertFalse(event instanceof PausedEvent, "no pause in game 2: Blue is still substituted");
            }
            assertTrue(fixture.serverLog().contains("substituted: [BLUE]"), fixture.serverLog());
        }
    }

    @Test
    void shutdownDuringTheWaitEndsTheSessionAsAborted() throws Exception {
        ServerFixture fixture = new ServerFixture(ServerFixture.fastConfig().withRematchDelayMs(60_000));
        List<FakePlayer> players = fixture.joinFour(fixture.createGame(SEED), FakePlayer.Mode.AUTO);
        for (FakePlayer player : players)
            assertTrue(player.awaitGameOvers(1, 120_000));

        fixture.close(); // also asserts that the game thread has ended

        for (FakePlayer player : players) {
            assertTrue(player.awaitGameOvers(2, 5_000), player.colour + " got no final GAME_OVER");
            List<GameOverEvent> overs = player.seen(GameOverEvent.class);
            GameOverEvent last = overs.get(overs.size() - 1);
            assertEquals(GameStatus.ABORTED, last.status());
            assertFalse(last.hasNextGame());
            assertTrue(player.seen(NewGameEvent.class).isEmpty());
        }
    }

    @Test
    void withRematchOffTheGameOverAnnouncesNoNextGame() throws Exception {
        try (ServerFixture fixture = new ServerFixture(ServerFixture.fastConfig())) {
            String gameId = fixture.createGame(SEED);
            List<FakePlayer> players = fixture.joinFour(gameId, FakePlayer.Mode.AUTO);
            for (FakePlayer player : players) {
                assertTrue(player.gameOver.await(120, TimeUnit.SECONDS));
                assertFalse(player.result.hasNextGame());
                assertTrue(player.seen(NewGameEvent.class).isEmpty());
            }
            Map<String, Object> summary = JsonParser.parseObject(fixture.get("/games/" + gameId).body());
            assertEquals(1L, summary.get("gameNumber"));
            assertEquals(SEED, summary.get("seed"));
        }
    }

    private static <T extends ServerEvent> void awaitFirst(FakePlayer player, Class<T> type) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(120);
        while (player.seen(type).isEmpty()) {
            assertTrue(System.nanoTime() < deadline, player.colour + " saw no " + type.getSimpleName());
            Thread.sleep(20); // test pacing only
        }
    }

    private static List<ServerEvent> snapshotOf(List<ServerEvent> seen) {
        synchronized (seen) {
            return new ArrayList<>(seen);
        }
    }

    private static int indexOf(List<ServerEvent> events, Class<? extends ServerEvent> type, int from) {
        for (int i = Math.max(0, from); i < events.size(); i++)
            if (type.isInstance(events.get(i)))
                return i;
        return -1;
    }
}
