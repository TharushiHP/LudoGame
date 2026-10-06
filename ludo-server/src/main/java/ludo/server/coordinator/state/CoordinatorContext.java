package ludo.server.coordinator.state;

import ludo.shared.PlayerColor;
import ludo.shared.protocol.JoinRequest;

/**
 * What a {@link CoordinatorState} may ask of the game it belongs to. The coordinator implements it;
 * states depend only on this interface (Dependency Inversion), which also keeps the state package
 * free of a cycle with the coordinator package. Called on the game thread only.
 */
public interface CoordinatorContext {

    boolean isSeatTaken(PlayerColor colour);

    /** Records a JOIN and returns how many colours have joined so far. */
    int takeSeat(JoinRequest join);

    /** Version of the last STATE broadcast (0 before the first). */
    long currentVersion();

    /** Hash of the last STATE broadcast (null before the first). */
    String currentHash();

    /** Sends the last STATE again to that colour's event streams only (after a hash mismatch). */
    void resendStateTo(PlayerColor colour);
}
