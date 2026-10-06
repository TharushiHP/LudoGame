package ludo.server.coordinator.state;

import ludo.shared.protocol.AckCommand;

/**
 * Handles an ACK that arrives outside the ack barrier, e.g. after a client reconnected and was
 * sent the current STATE again. A matching ACK of the current version is harmless and gets 200;
 * an old version is stale (409); a wrong hash gets the STATE re-sent (409).
 */
final class LateAck {

    private LateAck() {}

    static Reply check(AckCommand ack, CoordinatorContext ctx) {
        if (ack.version() != ctx.currentVersion())
            return Reply.conflict("stale ACK: version " + ack.version() + ", current version is " + ctx.currentVersion());
        if (!ack.hash().equals(ctx.currentHash())) {
            ctx.resendStateTo(ack.colour());
            return Reply.conflict("hash mismatch for version " + ack.version() + "; STATE re-sent");
        }
        return Reply.okWith("acked", true, "version", ack.version());
    }
}
