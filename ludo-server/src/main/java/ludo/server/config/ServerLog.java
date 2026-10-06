package ludo.server.config;

import java.io.PrintStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * The server's evidence log. Every line starts with the time and the name of the thread that wrote
 * it, so the log itself shows which thread did what (e.g. only "game-1" ever applies a command).
 * Thread-safe: PrintStream.println is synchronized, so lines from different threads never mix.
 */
public final class ServerLog {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final PrintStream out;

    public ServerLog(PrintStream out) {
        this.out = out;
    }

    public void log(String message) {
        out.println(LocalTime.now().format(TIME) + " [" + Thread.currentThread().getName() + "] " + message);
    }

    /** A line exactly as given, without time or thread (used to echo the game's own log). */
    public void raw(String line) {
        out.println(line);
    }
}
