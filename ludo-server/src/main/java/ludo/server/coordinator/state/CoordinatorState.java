package ludo.server.coordinator.state;

import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.JoinRequest;
import ludo.shared.protocol.RequestType;
import ludo.shared.protocol.RollCommand;

/**
 * One state of a game's coordinator (State pattern). The coordinator hands every client request
 * to its current state, and the state alone decides whether that request is valid now.
 * By default every request is rejected with 409, so each state overrides only the requests it
 * accepts (Open/Closed: a new state never changes the existing ones).
 * States are used on the game thread only, so they need no locks.
 */
public interface CoordinatorState {

    /** Short name for logs and GET /games, e.g. {@code AwaitingRoll(RED,#4)}. */
    String name();

    default Reply onJoin(JoinRequest request, CoordinatorContext ctx) {
        return notAccepted(RequestType.JOIN);
    }

    default Reply onRoll(RollCommand request, CoordinatorContext ctx) {
        return notAccepted(RequestType.ROLL);
    }

    default Reply onDecision(DecisionReply request, CoordinatorContext ctx) {
        return notAccepted(RequestType.DECISION);
    }

    default Reply onAck(AckCommand request, CoordinatorContext ctx) {
        return notAccepted(RequestType.ACK);
    }

    default Reply notAccepted(RequestType type) {
        return Reply.conflict(type + " not accepted while " + name());
    }
}
