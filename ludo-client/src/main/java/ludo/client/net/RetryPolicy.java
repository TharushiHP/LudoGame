package ludo.client.net;

/**
 * How many times, and after what wait, a request that failed with a network error or 503 is sent
 * again (Value Object). The wait doubles after each attempt, capped at {@code maxDelayMs}.
 */
public record RetryPolicy(int maxAttempts, long initialDelayMs, long maxDelayMs) {

    /** 6 attempts, with waits of 250, 500, 1000, 2000 and 2000 ms between them. */
    public static RetryPolicy standard() {
        return new RetryPolicy(6, 250, 2_000);
    }

    /** The wait after attempt number {@code attempt} (the first try is attempt 1). */
    public long delayAfter(int attempt) {
        long delay = initialDelayMs << Math.min(attempt - 1, 20);
        return Math.min(delay, maxDelayMs);
    }
}
