package ludo.testclients;

import ludo.testclients.report.RequestLog;
import ludo.testclients.report.ScenarioResult;
import ludo.testclients.report.SummaryReport;
import ludo.testclients.scenario.BurstScenario;
import ludo.testclients.scenario.CreateScenario;
import ludo.testclients.scenario.PlayScenario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Runs each scenario, small, against a real server started in-process, and asserts its checks:
 * the evidence that the server queues simultaneous requests from fast automatic clients and still
 * keeps every game consistent.
 */
class ScenarioTest {

    @TempDir
    Path dir;

    private TestClientOptions options(TestServer server) {
        return TestClientOptions.defaults().withServer(server.url()).withOutDir(dir);
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void threeGamesPlayedAtTheSameTimeAllEndConsistently() throws Exception {
        try (TestServer server = new TestServer(c -> c)) {
            ScenarioResult result = new PlayScenario().run(options(server).withGames(3));

            assertTrue(result.passed(), SummaryReport.render(result));
            assertEquals(3, result.servers().size());
            assertTrue(result.logs().values().iterator().next().requests() > 3 * 4 * 10, "a real game is many requests");
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void twoBurstClientsAreQueuedAndTheGameStaysConsistent() throws Exception {
        try (TestServer server = new TestServer(c -> c)) {
            ScenarioResult result = new BurstScenario().run(options(server).withClients(2).withRequests(200));

            String report = SummaryReport.render(result);
            assertTrue(result.passed(), report);
            assertTrue(result.peakQueueDepth() > 1, "requests waited in the queue: " + report);
            assertTrue(result.check("a repeated requestId gets the identical stored reply").passed(), report);
            assertTrue(result.check("at most one ROLL accepted per turnId").passed(), report);
            assertTrue(server.log().matches("(?s).*queue=[1-9].*"), "the game thread saw commands waiting behind the one it took");
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void aFullQueueAnswers503AndEveryOneIsRetriedUntilAccepted() throws Exception {
        try (TestServer server = new TestServer(c -> c.withQueueCapacity(4))) {
            ScenarioResult result = new BurstScenario().run(options(server).withClients(2).withRequests(200));

            String report = SummaryReport.render(result);
            long first503 = 0;
            long retries = 0;
            long attempts503 = 0;
            long final503 = 0;
            for (RequestLog log : result.logs().values()) {
                first503 += log.firstAttempt503();
                retries += log.retries();
                attempts503 += log.attempts503();
                final503 += log.final503();
            }
            assertTrue(first503 > 0, "a queue of 4 must overflow: " + report);
            assertEquals(0, final503, "every 503 was retried until the server took it: " + report);
            assertTrue(retries >= attempts503, "one retry per 503: " + report);
            assertEquals(4, result.servers().get(0).queueCapacity());
            assertTrue(result.servers().get(0).refused() > 0, "the server counted its 503s too");
            assertTrue(result.passed(), report);
        }
    }

    @Test
    @Timeout(value = 120, unit = TimeUnit.SECONDS)
    void underHeavyOverloadTheServerPushesBackAndEveryCheckStillPasses() throws Exception {
        // Queue of 4, four burst clients firing waves of 50 at once: the server refuses with 503 and
        // the clients retry. Retries that get in may arrive after many other accepted requests and
        // must still get their stored reply. (Whether a copy uses up all its retries depends on the
        // machine's speed, so that is reported in the notes, not asserted.)
        try (TestServer server = new TestServer(c -> c.withQueueCapacity(4))) {
            ScenarioResult result = new BurstScenario().run(
                    options(server).withClients(4).withRequests(150).withWaveSize(50));

            String report = SummaryReport.render(result);
            long first503 = result.logs().values().stream().mapToLong(RequestLog::firstAttempt503).sum();
            long retries = result.logs().values().stream().mapToLong(RequestLog::retries).sum();
            assertEquals(4, result.servers().get(0).queueCapacity());
            assertTrue(first503 > 0, "a queue of 4 must push back with 503: " + report);
            assertTrue(retries > 0, "the 503s are retried: " + report);
            assertTrue(result.passed(), report);
            assertTrue(result.check("at most one ROLL accepted per turnId").passed(), report);
            assertTrue(result.check("a repeated requestId gets the identical stored reply").passed(), report);
            assertTrue(result.check("all 4 clients of each game and GET /state end with the same hash").passed(), report);
            assertTrue(result.check("every ACK hash matched (no client saw a different state)").passed(), report);
            assertTrue(result.notes().stream().anyMatch(n -> n.contains("duplicate copies gave up after all retries; never applied")),
                    report);
        }
    }

    @Test
    @Timeout(value = 60, unit = TimeUnit.SECONDS)
    void clientsCreatingGamesAtTheSameTimeGetUniqueIds() throws Exception {
        try (TestServer server = new TestServer(c -> c)) {
            ScenarioResult result = new CreateScenario().run(options(server).withClients(4).withCreates(25));

            assertTrue(result.passed(), SummaryReport.render(result));
            assertEquals(100, result.logs().values().iterator().next().requests());
        }
    }
}
