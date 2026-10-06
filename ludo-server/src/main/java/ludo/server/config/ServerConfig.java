package ludo.server.config;

import ludo.game.EndCondition;

import java.io.PrintStream;
import java.util.Locale;

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
 * @param endCondition      when a new game ends unless POST /games says otherwise (Rule 11);
 *                          the server stops at the first winner by default
 * @param rematchDelayMs    after a game ends, the server waits this long and then starts the next
 *                          game in the same session (same seats, new seed); 0 = no next game
 */
public record ServerConfig(int port, long turnDelayMs, long moveTimeoutMs, long substituteAfterMs,
                           int queueCapacity, long replyTimeoutMs, long keepAliveSeconds, int httpThreads,
                           boolean echoGameLog, PrintStream out, EndCondition endCondition,
                           long rematchDelayMs) {

    public static ServerConfig defaults() {
        return new ServerConfig(8080, 500, 10_000, 30_000, 64, 5_000, 10, 16, true, System.out,
                EndCondition.FIRST_WINNER, 10_000);
    }

    public ServerConfig withPort(int value) {
        return new ServerConfig(value, turnDelayMs, moveTimeoutMs, substituteAfterMs, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, out, endCondition, rematchDelayMs);
    }

    public ServerConfig withTurnDelayMs(long value) {
        return new ServerConfig(port, value, moveTimeoutMs, substituteAfterMs, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, out, endCondition, rematchDelayMs);
    }

    public ServerConfig withMoveTimeoutMs(long value) {
        return new ServerConfig(port, turnDelayMs, value, substituteAfterMs, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, out, endCondition, rematchDelayMs);
    }

    public ServerConfig withSubstituteAfterMs(long value) {
        return new ServerConfig(port, turnDelayMs, moveTimeoutMs, value, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, out, endCondition, rematchDelayMs);
    }

    public ServerConfig withQueueCapacity(int value) {
        return new ServerConfig(port, turnDelayMs, moveTimeoutMs, substituteAfterMs, value,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, out, endCondition, rematchDelayMs);
    }

    public ServerConfig withEchoGameLog(boolean value) {
        return new ServerConfig(port, turnDelayMs, moveTimeoutMs, substituteAfterMs, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, value, out, endCondition, rematchDelayMs);
    }

    public ServerConfig withOut(PrintStream value) {
        return new ServerConfig(port, turnDelayMs, moveTimeoutMs, substituteAfterMs, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, value, endCondition, rematchDelayMs);
    }

    /** "FIRST_WINNER" or "ALL_PLACES", in any case: for --end-condition and the endCondition field of POST /games. */
    public static EndCondition parseEndCondition(String text) {
        try {
            return EndCondition.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("end condition must be FIRST_WINNER or ALL_PLACES, not " + text);
        }
    }

    public ServerConfig withEndCondition(EndCondition value) {
        return new ServerConfig(port, turnDelayMs, moveTimeoutMs, substituteAfterMs, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, out, value, rematchDelayMs);
    }

    /** 0 turns the automatic next game off (the tests' fixture does that). */
    public ServerConfig withRematchDelayMs(long value) {
        return new ServerConfig(port, turnDelayMs, moveTimeoutMs, substituteAfterMs, queueCapacity,
                replyTimeoutMs, keepAliveSeconds, httpThreads, echoGameLog, out, endCondition, value);
    }
}
