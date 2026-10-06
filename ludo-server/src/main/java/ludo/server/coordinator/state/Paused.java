package ludo.server.coordinator.state;

import ludo.shared.PlayerColor;
import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.RollCommand;

/**
 * State while the game is paused because {@code missing} did not answer in time. It wraps the
 * state that was waiting (AwaitingRoll or AwaitingDecision) and passes every request on to it, so
 * the missing ROLL or DECISION is still accepted and the game resumes as soon as it arrives.
 */
public final class Paused implements CoordinatorState {

    private final PlayerColor missing;
    private final CoordinatorState waiting;

    public Paused(PlayerColor missing, CoordinatorState waiting) {
        this.missing = missing;
        this.waiting = waiting;
    }

    @Override
    public String name() {
        return "Paused(" + missing + ", " + waiting.name() + ")";
    }

    @Override
    public Reply onJoin(JoinRequest request, CoordinatorContext ctx) {
        return waiting.onJoin(request, ctx);
    }

    @Override
    public Reply onRoll(RollCommand request, CoordinatorContext ctx) {
        return waiting.onRoll(request, ctx);
    }

    @Override
    public Reply onDecision(DecisionReply request, CoordinatorContext ctx) {
        return waiting.onDecision(request, ctx);
    }

    @Override
    public Reply onAck(AckCommand request, CoordinatorContext ctx) {
        return waiting.onAck(request, ctx);
    }
}
