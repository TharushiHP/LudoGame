package ludo.testclients.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Turns a {@link ScenarioResult} into the plain-text summary table that is printed and saved as
 * {@code testclients-<scenario>-<yyyyMMdd-HHmmss>.txt} for the report.
 */
public final class SummaryReport {

    private static final DateTimeFormatter SHOWN = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private static final DateTimeFormatter FILE = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String ROW = "%-8s %6s %8s %6s %6s %9s %8s %9s %6s %8s %8s %8s %8s%n";

    private SummaryReport() {}

    public static String render(ScenarioResult result) {
        StringBuilder out = new StringBuilder();
        out.append("LUDO-T test clients: ").append(result.scenario()).append('\n');
        out.append(String.format(Locale.ROOT, "Server   %s   started %s   duration %.1f s%n", result.server(),
                result.started().format(SHOWN), result.duration().toMillis() / 1000.0));
        out.append("Options  ").append(result.options()).append('\n');
        for (Map.Entry<String, RequestLog> entry : result.logs().entrySet()) {
            out.append('\n').append("Requests: ").append(entry.getKey()).append('\n');
            table(out, entry.getValue().rows());
        }
        if (!result.servers().isEmpty()) {
            out.append('\n').append("Server queue (GET /games/{id})").append('\n');
            out.append(String.format(Locale.ROOT, "%-6s %-14s %13s %14s %9s %9s %8s %12s%n", "game", "state",
                    "queueCapacity", "peakQueueDepth", "accepted", "rejected", "refused", "otherErrors"));
            for (ServerStats s : result.servers())
                out.append(String.format(Locale.ROOT, "%-6s %-14s %13d %14d %9d %9d %8d %12d%n", s.gameId(),
                        shorten(s.state(), 14), s.queueCapacity(), s.peakQueueDepth(), s.accepted(), s.rejected(),
                        s.refused(), s.otherErrors()));
        }
        if (!result.notes().isEmpty()) {
            out.append('\n').append("Notes").append('\n');
            result.notes().forEach(note -> out.append("  ").append(note).append('\n'));
        }
        out.append('\n').append("Checks").append('\n');
        for (Check check : result.checks())
            out.append(check.passed() ? "PASS  " : "FAIL  ").append(check.name())
                    .append(check.detail().isEmpty() ? "" : " (" + check.detail() + ")").append('\n');
        out.append('\n').append("Result: ").append(result.passed() ? "PASS" : "FAIL").append('\n');
        return out.toString();
    }

    /** Writes the summary to {@code dir} (created if needed) and returns the file. */
    public static Path write(ScenarioResult result, Path dir) throws IOException {
        Files.createDirectories(dir);
        Path file = dir.resolve("testclients-" + result.scenario() + "-" + result.started().format(FILE) + ".txt");
        Files.writeString(file, render(result), StandardCharsets.UTF_8);
        return file;
    }

    private static void table(StringBuilder out, List<RequestLog.Row> rows) {
        out.append(String.format(Locale.ROOT, ROW, "type", "sent", "attempts", "2xx", "409", "503(1st)", "retries",
                "final503", "other", "min ms", "avg ms", "p95 ms", "max ms"));
        for (RequestLog.Row r : rows) {
            LatencyStats l = r.latency();
            out.append(String.format(Locale.ROOT, ROW, r.type(), r.sent(), r.attempts(), r.ok(), r.conflict(),
                    r.first503(), r.retries(), r.final503(), r.other(), ms(l.minMs()), ms(l.avgMs()),
                    ms(l.p95Ms()), ms(l.maxMs())));
        }
    }

    private static String ms(double value) {
        return String.format(Locale.ROOT, "%.1f", value);
    }

    private static String shorten(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max - 1) + "~";
    }
}
