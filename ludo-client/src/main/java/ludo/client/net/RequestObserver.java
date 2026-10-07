package ludo.client.net;

/**
 * Is told about every HTTP attempt {@link HttpServerGateway} makes and about every request's final
 * answer (Observer pattern). The client itself uses {@link #NONE}; the test clients plug in an
 * observer that counts attempts, 503s, retries and latencies without changing how requests are sent.
 * Methods are called on HttpClient threads, so implementations must be thread-safe.
 */
public interface RequestObserver {

    /** Observes nothing. */
    RequestObserver NONE = new RequestObserver() {};

    /**
     * One HTTP attempt has ended.
     *
     * @param attempt 1 for the first try, 2 for the first retry, ...
     * @param status  the HTTP status, or -1 after a network error
     * @param nanos   how long this attempt took
     */
    default void onAttempt(String method, String path, int attempt, int status, long nanos) {}

    /**
     * The request is finished: no more retries follow.
     *
     * @param attempts    how many attempts were made (1 = no retry)
     * @param finalStatus the last attempt's status, or -1 after a network error
     * @param totalNanos  from the first attempt to the end, retry waits included
     */
    default void onCompleted(String method, String path, int attempts, int finalStatus, long totalNanos) {}
}
