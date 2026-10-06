package ludo.server.coordinator.state;

import ludo.shared.protocol.AckCommand;
import ludo.shared.protocol.DecisionKind;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.DecisionRequest;

/**
 * State while the game waits for one colour's answer to a DECISION_REQUEST. Besides colour,
 * version and decisionId it checks the answer itself: a chosen piece must be one of the candidates.
 * Without that check a bad client answer would reach Game, which would stop the game thread.
 */
public final class AwaitingDecision implements CoordinatorState {

    private final DecisionRequest question;
    private DecisionReply answer;

    public AwaitingDecision(DecisionRequest question) {
        this.question = question;
    }

    public boolean hasAnswer() {
        return answer != null;
    }

    public DecisionReply answer() {
        return answer;
    }

    @Override
    public String name() {
        return "AwaitingDecision(" + question.colour() + ",#" + question.decisionId() + ")";
    }

    @Override
    public Reply onDecision(DecisionReply request, CoordinatorContext ctx) {
        if (request.colour() != question.colour())
            return Reply.conflict("waiting for " + question.colour() + "'s decision, not " + request.colour() + "'s");
        if (request.expectedVersion() != question.version())
            return Reply.conflict("stale version " + request.expectedVersion() + ", current version is " + question.version());
        if (request.decisionId() != question.decisionId())
            return Reply.conflict("stale decisionId " + request.decisionId() + ", current decisionId is " + question.decisionId());
        if (question.kind() == DecisionKind.MOVE_FROM_BASE && request.fromBase().isEmpty())
            return Reply.badRequest("a MOVE_FROM_BASE decision needs \"fromBase\"");
        if (question.kind() == DecisionKind.CHOOSE_PIECE && request.piece().isPresent()
                && !question.candidates().contains(request.piece().getAsInt()))
            return Reply.conflict("piece " + request.piece().getAsInt() + " is not one of the candidates " + question.candidates());
        answer = request;
        return Reply.okWith("accepted", true, "decisionId", question.decisionId());
    }

    @Override
    public Reply onAck(AckCommand request, CoordinatorContext ctx) {
        return LateAck.check(request, ctx);
    }
}
