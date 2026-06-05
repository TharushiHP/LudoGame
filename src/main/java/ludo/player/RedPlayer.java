package ludo.player;

import ludo.board.PlayerColor;
import ludo.player.strategy.AggressiveStrategy;

public class RedPlayer extends Player {

    public RedPlayer() {
        super(PlayerColor.RED, new AggressiveStrategy(PlayerColor.RED));
    }
}
