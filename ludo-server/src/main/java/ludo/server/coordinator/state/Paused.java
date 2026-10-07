package ludo.server.coordinator.state;

import ludo.shared.PlayerColor;
import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.RollCommand;

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
