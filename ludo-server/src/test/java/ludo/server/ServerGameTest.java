package ludo.server;

import ludo.shared.json.JsonParser;
import ludo.shared.protocol.SnapshotCodec;
import ludo.shared.protocol.StateHasher;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;
import org.junit.jupiter.api.Test;

import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end: a real server and four fake clients play a whole game over HTTP and Server-Sent
 * Events, each client deciding with its own strategy from the snapshots it receives.
 */
class ServerGameTest {

    private static final long SEED = 7;

    @Test
    void fourRemotePlayersFinishAGameInLockstep() throws Exception {
        try (ServerFixture fixture = new ServerFixture(ServerFixture.fastConfig())) {
            String gameId = fixture.createGame(SEED);
            List<FakePlayer> players = fixture.joinFour(gameId, FakePlayer.Mode.AUTO);

            List<Long> versionsSeen = pollStateWhileRunning(fixture, gameId, players.get(0));

            for (FakePlayer player : players) {
                assertTrue(player.gameOver.await(120, TimeUnit.SECONDS), player.colour + " saw no GAME_OVER");
                assertEquals(GameStatus.FINISHED, player.result.status());
                assertEquals(List.of(), player.problems, "every ROLL, DECISION and ACK must succeed and every hash match");
                assertFalse(player.ackStatuses.isEmpty());
                assertTrue(player.ackStatuses.stream().allMatch(status -> status == 200));
            }
            assertFalse(versionsSeen.isEmpty(), "GET /state answered while the game was running");
            assertTrue(fixture.serverLog().contains("[game-" + gameId + "] queue="),
                    "commands are applied on the game thread, and the log says so");
        }
    }

    /** The remote game is the same game as the console one: same seed, same strategies, same log. */
    @Test
    void remoteGameLogMatchesTheConsoleGoldenMaster() throws Exception {
        try (ServerFixture fixture = new ServerFixture(ServerFixture.fastConfig())) {
            String gameId = fixture.createGame(SEED);
            List<FakePlayer> players = fixture.joinFour(gameId, FakePlayer.Mode.AUTO);
            for (FakePlayer player : players)
                assertTrue(player.gameOver.await(120, TimeUnit.SECONDS));

            String golden = new String(GoldenMasterTest.golden(SEED), StandardCharsets.UTF_8);
            String expected = golden.substring(golden.indexOf('\n') + 1); // without ConsoleSimulation's "Seed:" line
            for (FakePlayer player : players) {
                StringBuilder actual = new StringBuilder();
                synchronized (player.gameLog) {
                    player.gameLog.forEach(line -> actual.append(line).append('\n'));
                }
                assertEquals(expected, actual.toString(), player.colour + "'s log differs from seed-7.txt");
            }
        }
    }

    // GET /state is served from the published snapshot, so it answers at any time during the game.
    private static List<Long> pollStateWhileRunning(ServerFixture fixture, String gameId, FakePlayer watcher)
            throws Exception {
        List<Long> versions = new ArrayList<>();
        long previous = 0;
        while (watcher.gameOver.getCount() > 0 && versions.size() < 20) {
            HttpResponse<String> response = fixture.get("/games/" + gameId + "/state");
            assertEquals(200, response.statusCode());
            Map<String, Object> json = JsonParser.parseObject(response.body());
            long version = (Long) json.get("version");
            assertTrue(version >= previous, "versions never go backwards");
            if (version > 0) {
                @SuppressWarnings("unchecked")
                GameSnapshot snapshot = SnapshotCodec.fromJson((Map<String, Object>) json.get("snapshot"));
                assertEquals(StateHasher.hash(snapshot), json.get("hash"), "published hash belongs to the published snapshot");
                versions.add(version);
            }
            previous = version;
            Thread.sleep(20); // test pacing only; the server itself never sleeps
        }
        return versions;
    }
}
