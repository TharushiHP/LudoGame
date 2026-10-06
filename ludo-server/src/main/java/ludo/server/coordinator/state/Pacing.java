package ludo.server.coordinator.state;

import ludo.shared.protocol.AckCommand;

/**
 * State during the turn delay after a roll has been acknowledged. The game thread keeps answering
 * requests (it waits on the queue, never sleeps); only a late ACK of the current state is valid.
 */
public final class Pacing implements CoordinatorState {

    private final long delayMs;

    public Pacing(long delayMs) {
        this.delayMs = delayMs;
    }

    @Override
    public String name() {
        return "Pacing(" + delayMs + "ms)";
    }

    @Override
    public Reply onAck(AckCommand request, CoordinatorContext ctx) {
        return LateAck.check(request, ctx);
    }
}
