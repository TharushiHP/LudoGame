package ludo.server.config;

import ludo.game.EndCondition;

import java.io.PrintStream;
import java.util.Locale;


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
