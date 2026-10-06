package ludo.server.coordinator;

import ludo.game.TurnGate;
import ludo.server.coordinator.state.AwaitingRoll;
import ludo.shared.PlayerColor;
import ludo.shared.protocol.RollRequest;
import ludo.shared.snapshot.GameSnapshot;

/**
 * The server's {@link TurnGate} (Adapter: turns Game's "before/after every roll" calls into the
 * lockstep protocol). Game calls it on the game thread, so every wait here is a wait on the
 * command queue, never a sleep:
 * <ol>
 *   <li>{@link #beforeRoll}: ROLL_REQUEST, then wait for that colour's ROLL.</li>
 *   <li>Game rolls, asks {@link RemoteMoveDecider} for decisions, and applies the move.</li>
 *   <li>{@link #afterRoll}: STATE broadcast, wait for all ACKs, then the pacing delay.</li>
 * </ol>
 */
final class RemoteTurnGate implements TurnGate {

    private final Coordinator coordinator;

    RemoteTurnGate(Coordinator coordinator) {
        this.coordinator = coordinator;
    }

    @Override
    public void beforeRoll(PlayerColor color) throws InterruptedException {
        coordinator.ensureFirstState();
        long turnId = coordinator.nextTurnId();
        if (coordinator.isSubstituted(color)) {
            coordinator.log("the server rolls for " + color + " (substituted), turnId " + turnId);
            return;
        }
        long version = coordinator.currentVersion();
        AwaitingRoll waiting = new AwaitingRoll(color, turnId, version);
        coordinator.awaitPlayer(color, waiting, new RollRequest(color, turnId, version), waiting::isDone);
    }

    @Override
    public void afterRoll(GameSnapshot snapshot) throws InterruptedException {
        // RemoteMoveDecider cannot throw InterruptedException; it restores the flag instead,
        // and the game stops here, at the next point that is allowed to throw it.
        if (Thread.interrupted())
            throw new InterruptedException("game thread interrupted");
        coordinator.publishStateAndAwaitAcks(snapshot);
        coordinator.pace();
    }
}
