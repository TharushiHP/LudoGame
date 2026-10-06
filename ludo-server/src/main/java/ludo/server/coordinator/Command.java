package ludo.server.coordinator;

import ludo.server.coordinator.state.Reply;
import ludo.shared.protocol.ClientRequest;

import java.util.concurrent.CompletableFuture;

/**
 * An item on a game's command queue (Command pattern). HTTP threads produce {@link Request}s and
 * wait on the reply future; the event-stream code produces {@link Connected} and {@link Lost}.
 * Only the game thread consumes them, so it alone ever changes the game (producer-consumer).
 */
sealed interface Command {

    /** A client request; the game thread completes {@code reply} once it has handled it. */
    record Request(ClientRequest request, CompletableFuture<Reply> reply) implements Command {}

    /** A client opened an event stream (first connection or reconnect). */
    record Connected(EventSink sink) implements Command {}

    /** A client's event stream failed and was removed. */
    record Lost(EventSink sink) implements Command {}
}
