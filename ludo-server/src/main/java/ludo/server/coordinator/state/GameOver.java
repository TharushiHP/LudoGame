package ludo.server.coordinator.state;

import ludo.shared.snapshot.GameStatus;

/** Final state: the game has ended (or was aborted) and every request is rejected with 409. */
public final class GameOver implements CoordinatorState {

    private final GameStatus status;

    public GameOver(GameStatus status) {
        this.status = status;
    }

    @Override
    public String name() {
        return "GameOver(" + status + ")";
    }
}
