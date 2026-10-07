package ludo.testclients.report;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Minimum, average, 95th percentile and maximum of a set of latencies, in milliseconds (Value
 * Object). The 95th percentile uses the nearest-rank method: the smallest value that at least 95 %
 * of the samples are not above.
 */
public record LatencyStats(int count, double minMs, double avgMs, double p95Ms, double maxMs) {

    public static LatencyStats of(List<Long> nanos) {
        if (nanos.isEmpty())
            return new LatencyStats(0, 0, 0, 0, 0);
        List<Long> sorted = new ArrayList<>(nanos);
        Collections.sort(sorted);
        double sum = 0;
        for (long value : sorted)
            sum += value;
        int rank = (int) Math.ceil(0.95 * sorted.size()); // 1-based
        return new LatencyStats(sorted.size(), ms(sorted.get(0)), ms(sum / sorted.size()),
                ms(sorted.get(rank - 1)), ms(sorted.get(sorted.size() - 1)));
    }

    private static double ms(double nanos) {
        return nanos / 1_000_000.0;
    }
}
