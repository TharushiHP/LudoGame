package ludo.server.coordinator;

import ludo.server.coordinator.state.Reply;
import ludo.shared.protocol.ClientRequest;

import java.util.concurrent.CompletableFuture;

sealed interface Command {

    /**
     * A client request; the game thread completes {@code reply} once it has handled
     * it.
     */
    record Request(ClientRequest request, CompletableFuture<Reply> reply) implements Command {
    }

    /** A client opened an event stream (first connection or reconnect). */
    record Connected(EventSink sink) implements Command {
    }

    /** A client's event stream failed and was removed. */
    record Lost(EventSink sink) implements Command {
    }
}
