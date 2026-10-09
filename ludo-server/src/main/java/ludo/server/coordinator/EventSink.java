package ludo.server.coordinator;

import ludo.shared.PlayerColor;

import java.io.IOException;


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
