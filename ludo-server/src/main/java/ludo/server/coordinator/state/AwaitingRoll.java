package ludo.server.coordinator.state;

import ludo.shared.PlayerColor;
import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.RollCommand;

/**
 * State while the game waits for one colour's ROLL. A ROLL is accepted only from that colour,
 * for this turnId and the current version; anything else gets 409 with the reason.
 * {@link #isDone()} tells the waiting game thread that the ROLL has arrived.
 */
public final class AwaitingRoll implements CoordinatorState {

    private final PlayerColor colour;
    private final long turnId;
    private final long version;
    private boolean done;

    public AwaitingRoll(PlayerColor colour, long turnId, long version) {
        this.colour = colour;
        this.turnId = turnId;
        this.version = version;
    }

    public boolean isDone() {
        return done;
    }

    @Override
    public String name() {
        return "AwaitingRoll(" + colour + ",#" + turnId + ")";
    }

    @Override
    public Reply onRoll(RollCommand request, CoordinatorContext ctx) {
        if (request.colour() != colour)
            return Reply.conflict("waiting for " + colour + " to roll, not " + request.colour());
        if (request.expectedVersion() != version)
            return Reply.conflict("stale version " + request.expectedVersion() + ", current version is " + version);
        if (request.turnId() != turnId)
            return Reply.conflict("stale turnId " + request.turnId() + ", current turnId is " + turnId);
        done = true;
        return Reply.okWith("accepted", true, "turnId", turnId);
    }

    @Override
    public Reply onAck(AckCommand request, CoordinatorContext ctx) {
        return LateAck.check(request, ctx);
    }
}
