package ludo.server.config;

import java.io.PrintStream;

/**
 * Every server setting in one immutable place (Value Object, Java record). Defaults come from
 * {@link #defaults()}; the {@code with...} methods return a changed copy, so tests can shorten
 * timeouts without touching anything else.
 *
 * @param port              HTTP port; 0 picks a free port (tests)
 * @param turnDelayMs       pacing pause after each roll's acknowledgements
 * @param moveTimeoutMs     how long a ROLL, DECISION or all ACKs may take before the game reacts
 * @param substituteAfterMs how long a paused game waits before the server plays the missing colour
 * @param queueCapacity     commands that may wait for a game thread; more get HTTP 503
 * @param replyTimeoutMs    how long an HTTP thread waits for the game thread's reply (then 504)
 * @param keepAliveSeconds  interval of the event-stream keep-alive comments
 * @param httpThreads       size of the HTTP worker pool
 * @param echoGameLog       print each game's log lines on the server console
 * @param out               where the server log goes
 */
public record ServerConfig(int port, long turnDelayMs, long moveTimeoutMs, long substituteAfterMs,
                           int queueCapacity, long replyTimeoutMs, long keepAliveSeconds, int httpThreads,
                           boolean echoGameLog, PrintStream out) {

    public static ServerConfig defaults() {
        return new ServerConfig(8080, 500, 10_000, 30_000, 64, 5_000, 10, 16, true, System.out);
    }

    public ServerConfig withPort(int value) {
        return new ServerConfig(value, turnDelayMs, moveTimeoutMs, substituteAfterMs, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, out);
    }

    public ServerConfig withTurnDelayMs(long value) {
        return new ServerConfig(port, value, moveTimeoutMs, substituteAfterMs, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, out);
    }

    public ServerConfig withMoveTimeoutMs(long value) {
        return new ServerConfig(port, turnDelayMs, value, substituteAfterMs, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, out);
    }

    public ServerConfig withSubstituteAfterMs(long value) {
        return new ServerConfig(port, turnDelayMs, moveTimeoutMs, value, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, out);
    }

    public ServerConfig withQueueCapacity(int value) {
        return new ServerConfig(port, turnDelayMs, moveTimeoutMs, substituteAfterMs, value,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, out);
    }

    public ServerConfig withEchoGameLog(boolean value) {
        return new ServerConfig(port, turnDelayMs, moveTimeoutMs, substituteAfterMs, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, value, out);
    }

    public ServerConfig withOut(PrintStream value) {
        return new ServerConfig(port, turnDelayMs, moveTimeoutMs, substituteAfterMs, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, value);
    }
}
