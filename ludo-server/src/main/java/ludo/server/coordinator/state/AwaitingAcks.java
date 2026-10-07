package ludo.server.coordinator.state;

import ludo.shared.protocol.AckCommand;

public final class AwaitingAcks implements CoordinatorState {

    private final AckBarrier barrier;

    public AwaitingAcks(AckBarrier barrier) {
        this.barrier = barrier;
    }

    @Override
    public String name() {
        return "AwaitingAcks(v" + barrier.version() + ", waiting for " + barrier.pending() + ")";
    }

    @Override
    public Reply onAck(AckCommand request, CoordinatorContext ctx) {
        if (request.version() != barrier.version())
            return Reply
                    .conflict("stale ACK: version " + request.version() + ", waiting for version " + barrier.version());
        if (!request.hash().equals(barrier.hash())) {
            if (barrier.mayResend(request.colour())) {
                ctx.resendStateTo(request.colour());
                return Reply.conflict("hash mismatch for version " + barrier.version() + "; STATE re-sent");
            }
            barrier.drop(request.colour());
            return Reply.conflict("hash mismatch for version " + barrier.version()
                    + " again; " + request.colour() + " is left out of this barrier");
        }
        barrier.acked(request.colour());
        return Reply.okWith("acked", true, "version", barrier.version());
    }
}
