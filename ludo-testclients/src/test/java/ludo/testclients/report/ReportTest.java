package ludo.testclients.report;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** The numbers in the summary: latency statistics, request counting and the saved file. */
class ReportTest {

    @TempDir
    Path dir;

    @Test
    void latencyStatisticsUseTheNearestRankPercentile() {
        List<Long> nanos = new ArrayList<>();
        for (long ms = 1; ms <= 100; ms++)
            nanos.add(ms * 1_000_000);
        LatencyStats stats = LatencyStats.of(nanos);
        assertEquals(1.0, stats.minMs());
        assertEquals(50.5, stats.avgMs(), 1e-9);
        assertEquals(95.0, stats.p95Ms());
        assertEquals(100.0, stats.maxMs());
        assertEquals(0, LatencyStats.of(List.of()).count());
    }

    @Test
    void requestLogCountsFirst503sRetriesAndFinalAnswers() {
        RequestLog log = new RequestLog();
        log.onAttempt("POST", "/games/1/roll", 1, 503, 1_000_000);
        log.onAttempt("POST", "/games/1/roll", 2, 200, 1_000_000);
        log.onCompleted("POST", "/games/1/roll", 2, 200, 300_000_000);
        log.onAttempt("POST", "/games/1/ack", 1, 409, 1_000_000);
        log.onCompleted("POST", "/games/1/ack", 1, 409, 2_000_000);
        log.onAttempt("POST", "/games", 1, 201, 1_000_000);
        log.onCompleted("POST", "/games", 1, 201, 2_000_000);

        assertEquals(3, log.requests());
        assertEquals(1, log.firstAttempt503());
        assertEquals(1, log.attempts503());
        assertEquals(1, log.retries());
        assertEquals(0, log.final503());
        assertEquals(0, log.unexpected());
        List<RequestLog.Row> rows = log.rows();
        assertEquals(List.of("ack", "create", "roll", "total"), rows.stream().map(RequestLog.Row::type).toList());
        RequestLog.Row total = rows.get(3);
        assertEquals(3, total.sent());
        assertEquals(4, total.attempts());
        assertEquals(2, total.ok());
        assertEquals(1, total.conflict());
    }

    @Test
    void summaryIsRenderedAndSaved() throws Exception {
        RequestLog log = new RequestLog();
        log.onAttempt("POST", "/games/1/roll", 1, 200, 1_000_000);
        log.onCompleted("POST", "/games/1/roll", 1, 200, 1_000_000);
        ScenarioResult result = new ScenarioResult("burst", "http://localhost:1", "clients=2",
                LocalDateTime.of(2026, 10, 7, 14, 3, 12), Duration.ofSeconds(2), Map.of("burst clients", log),
                List.of(new ServerStats("1", "GameOver", 64, 9, 10, 5, 0, 0)),
                List.of(Check.of("something held", true, "1/1")), List.of("a note"));

        Path file = SummaryReport.write(result, dir.resolve("logs"));

        assertEquals("testclients-burst-20261007-140312.txt", file.getFileName().toString());
        String text = Files.readString(file);
        assertTrue(text.contains("peakQueueDepth"), text);
        assertTrue(text.contains("PASS  something held (1/1)"), text);
        assertTrue(text.contains("Result: PASS"), text);
        assertEquals(9, result.peakQueueDepth());
    }
}
