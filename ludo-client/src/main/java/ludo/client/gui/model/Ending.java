package ludo.client.gui.model;

import ludo.shared.PlayerColor;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.snapshot.GameStatus;


public record Ending(Kind kind, PlayerColor winner) {

    public enum Kind { FIRST_WINNER, PODIUM }

    public static Ending of(GameOverEvent over) {
        if (over.status() == GameStatus.FINISHED) {
            PlayerColor only = null;
            int placed = 0;
            for (var entry : over.finishPositions().entrySet())
                if (entry.getValue() != null && entry.getValue() > 0) {
                    placed++;
                    only = entry.getKey();
                }
            if (placed == 1)
                return new Ending(Kind.FIRST_WINNER, only);
        }
        return new Ending(Kind.PODIUM, null);
    }

    public boolean isFirstWinner() {
        return kind == Kind.FIRST_WINNER;
    }
}
