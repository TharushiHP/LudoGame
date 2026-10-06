package ludo.server.coordinator;

import ludo.game.GameEvent;
import ludo.game.GameEventListener;
import ludo.server.config.ServerLog;

import java.util.ArrayList;
import java.util.List;

/**
 * Collects the game's log messages until the next STATE broadcast takes them (Observer pattern,
 * concrete observer of Game). Optionally echoes each one to the server console exactly as the
 * console simulation would print it. Called on the game thread only.
 */
final class BufferingEventListener implements GameEventListener {

    private final List<String> buffer = new ArrayList<>();
    private final boolean echo;
    private final ServerLog log;

    BufferingEventListener(boolean echo, ServerLog log) {
        this.echo = echo;
        this.log = log;
    }

    @Override
    public void onEvent(GameEvent event, String message) {
        buffer.add(message);
        if (echo)
            log.raw(message);
    }

    /** The messages since the last call, oldest first; the buffer is then empty. */
    List<String> drain() {
        List<String> messages = List.copyOf(buffer);
        buffer.clear();
        return messages;
    }
}
