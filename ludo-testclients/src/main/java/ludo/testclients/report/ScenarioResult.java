package ludo.testclients.report;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Everything one scenario run produced (Value Object): the request logs of each group of clients
 * (e.g. "burst clients", "players"), the queue statistics the server reported for its games, the
 * checks, and a few free-text notes. {@link SummaryReport} turns it into the summary table.
 */
public record ScenarioResult(String scenario, String server, String options, LocalDateTime started, Duration duration,
                             Map<String, RequestLog> logs, List<ServerStats> servers, List<Check> checks,
                             List<String> notes) {

    public ScenarioResult {
        logs = new LinkedHashMap<>(logs);
        servers = List.copyOf(servers);
        checks = List.copyOf(checks);
        notes = List.copyOf(notes);
    }

    /** True when every check passed (and there was at least one). */
    public boolean passed() {
        return !checks.isEmpty() && checks.stream().allMatch(Check::passed);
    }

    /** The check with this name; fails loudly if there is none (tests use it). */
    public Check check(String name) {
        return checks.stream().filter(c -> c.name().equals(name)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no check named " + name + " in " + checks));
    }

    /** The highest peakQueueDepth of all this scenario's games. */
    public int peakQueueDepth() {
        return servers.stream().mapToInt(ServerStats::peakQueueDepth).max().orElse(0);
    }
}
