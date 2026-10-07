package ludo.testclients.report;

import ludo.client.net.RequestObserver;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Records every HTTP attempt and every finished request of a group of test clients (an
 * {@link RequestObserver} plugged into their {@code HttpServerGateway}s). Many HttpClient threads
 * call it at once, so it only appends to lock-free {@link ConcurrentLinkedQueue}s; the numbers are
 * worked out afterwards, when the scenario is over.
 */
public final class RequestLog implements RequestObserver {

    /** One HTTP attempt: status -1 means a network error. */
    public record Attempt(String type, int attempt, int status) {}

    /** One request after its last attempt. */
    public record Completed(String type, int attempts, int finalStatus, long totalNanos) {}

    private final ConcurrentLinkedQueue<Attempt> attempts = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Completed> completed = new ConcurrentLinkedQueue<>();

    @Override
    public void onAttempt(String method, String path, int attempt, int status, long nanos) {
        attempts.add(new Attempt(typeOf(method, path), attempt, status));
    }

    @Override
    public void onCompleted(String method, String path, int attempts, int finalStatus, long totalNanos) {
        completed.add(new Completed(typeOf(method, path), attempts, finalStatus, totalNanos));
    }

    /** "roll", "ack", "decision", "join" (the last path part), or "create" for POST /games. */
    static String typeOf(String method, String path) {
        String[] parts = path.split("/");
        String last = parts.length == 0 ? "" : parts[parts.length - 1];
        return method.equals("POST") && last.equals("games") ? "create" : last;
    }

    public List<Attempt> attempts() {
        return new ArrayList<>(attempts);
    }

    public List<Completed> completed() {
        return new ArrayList<>(completed);
    }

    /** Requests answered so far (after retries). */
    public int requests() {
        return completed.size();
    }

    /** First attempts refused with 503: the server's queue was full at that moment. */
    public long firstAttempt503() {
        return attempts.stream().filter(a -> a.attempt() == 1 && a.status() == 503).count();
    }

    /** Every attempt refused with 503, retries included. */
    public long attempts503() {
        return attempts.stream().filter(a -> a.status() == 503).count();
    }

    /** Attempts after the first: each one is a retry after a 503 or a network error. */
    public long retries() {
        return attempts.stream().filter(a -> a.attempt() > 1).count();
    }

    /** Requests whose last attempt still got 503 (all retries used up). */
    public long final503() {
        return completed.stream().filter(c -> c.finalStatus() == 503).count();
    }

    /** Requests that ended with a status other than 2xx, 409 or 503, or with a network error. */
    public long unexpected() {
        return completed.stream().filter(c -> !isExpected(c.finalStatus())).count();
    }

    private static boolean isExpected(int status) {
        return (status >= 200 && status < 300) || status == 409 || status == 503;
    }

    /** One summary row per request type, in alphabetical order, then a "total" row. */
    public List<Row> rows() {
        Map<String, List<Completed>> byType = new TreeMap<>();
        for (Completed c : completed)
            byType.computeIfAbsent(c.type(), t -> new ArrayList<>()).add(c);
        List<Attempt> allAttempts = attempts();
        List<Row> rows = new ArrayList<>();
        byType.forEach((type, list) -> rows.add(Row.of(type, list,
                allAttempts.stream().filter(a -> a.type().equals(type)).toList())));
        if (!rows.isEmpty())
            rows.add(Row.of("total", completed(), allAttempts));
        return rows;
    }

    /** The numbers of one table row. */
    public record Row(String type, int sent, int attempts, long ok, long conflict, long first503, long retries,
                      long final503, long other, LatencyStats latency) {

        static Row of(String type, List<Completed> done, List<Attempt> tries) {
            return new Row(type, done.size(), tries.size(),
                    done.stream().filter(c -> c.finalStatus() >= 200 && c.finalStatus() < 300).count(),
                    done.stream().filter(c -> c.finalStatus() == 409).count(),
                    tries.stream().filter(a -> a.attempt() == 1 && a.status() == 503).count(),
                    tries.stream().filter(a -> a.attempt() > 1).count(),
                    done.stream().filter(c -> c.finalStatus() == 503).count(),
                    done.stream().filter(c -> !isExpected(c.finalStatus())).count(),
                    LatencyStats.of(done.stream().map(Completed::totalNanos).toList()));
        }
    }
}
