package ludo.server.coordinator;

import ludo.shared.PlayerColor;

import java.io.IOException;

/**
 * One open event stream to one client (a port: the HTTP layer implements it with a Server-Sent
 * Events response). The coordinator depends only on this interface, never on HTTP classes
 * (Dependency Inversion), which also keeps the coordinator and http packages free of a cycle.
 * {@link #send} is only ever called by one thread at a time (the game's writer thread).
 */
public interface EventSink {

    /** The colour this stream belongs to, or null for a spectator. */
    PlayerColor colour();

    /** Short description for the log, e.g. {@code events#3(RED)}. */
    String name();

    /** Writes one complete event frame and flushes it; fails if the client has gone. */
    void send(String frame) throws IOException;

    /** Ends the stream. Never throws. */
    void close();
}
