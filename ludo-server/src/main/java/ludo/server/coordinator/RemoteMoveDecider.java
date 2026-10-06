package ludo.server.coordinator;

import ludo.players.SnapshotStrategyDecider;
import ludo.server.coordinator.state.AwaitingDecision;
import ludo.shared.PlayerColor;
import ludo.shared.decision.MoveDecider;
import ludo.shared.decision.PieceChoice;
import ludo.shared.protocol.DecisionKind;
import ludo.shared.protocol.DecisionReply;
import ludo.shared.protocol.DecisionRequest;
import ludo.shared.snapshot.GameSnapshot;

import java.util.List;

/**
 * The server's {@link MoveDecider} (Adapter / Remote Proxy): each decision Game asks for becomes a
 * DECISION_REQUEST to that colour's client, and the game thread waits on the command queue for the
 * matching DECISION. The client's memo (Blue's cycle position) comes back in the PieceChoice.
 * <p>
 * The local {@link SnapshotStrategyDecider} answers instead when the colour has been substituted,
 * or when the game thread is interrupted (shutdown): MoveDecider methods cannot throw
 * InterruptedException, so the flag is restored and the game stops at the next {@code afterRoll}.
 */
final class RemoteMoveDecider implements MoveDecider {

    private final Coordinator coordinator;
    private final MoveDecider local = new SnapshotStrategyDecider();

    RemoteMoveDecider(Coordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public PieceChoice choosePiece(GameSnapshot snapshot, PlayerColor color, int roll, List<Integer> candidatePieceIds) {
        if (coordinator.isSubstituted(color))
            return local.choosePiece(snapshot, color, roll, candidatePieceIds);
        DecisionReply answer = ask(new DecisionRequest(color, coordinator.nextDecisionId(), DecisionKind.CHOOSE_PIECE,
                roll, candidatePieceIds, coordinator.currentVersion(), snapshot));
        if (answer == null)
            return local.choosePiece(snapshot, color, roll, candidatePieceIds);
        return new PieceChoice(answer.piece(), answer.memo());
    }

    @Override
    public boolean prefersMoveFromBase(GameSnapshot snapshot, PlayerColor color) {
        if (coordinator.isSubstituted(color))
            return local.prefersMoveFromBase(snapshot, color);
        DecisionReply answer = ask(new DecisionRequest(color, coordinator.nextDecisionId(), DecisionKind.MOVE_FROM_BASE,
                snapshot.lastRoll(), List.of(), coordinator.currentVersion(), snapshot));
        if (answer == null)
            return local.prefersMoveFromBase(snapshot, color);
        return answer.fromBase().orElseThrow();
    }

    /** As declared by the client in its JOIN. */
    @Override
    public boolean triesOtherPiecesWhenBlocked(PlayerColor color) {
        return coordinator.triesOtherPiecesWhenBlocked(color);
    }

    /** The client's answer, or null when the local strategy must answer instead. */
    private DecisionReply ask(DecisionRequest question) {
        AwaitingDecision waiting = new AwaitingDecision(question);
        try {
            boolean answered = coordinator.awaitPlayer(question.colour(), waiting, question, waiting::hasAnswer);
            return answered ? waiting.answer() : null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            coordinator.log("interrupted while waiting for " + question.colour()
                    + "'s decision; answered locally, the game stops after this roll");
            return null;
        }
    }
}
