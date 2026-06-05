package ludo.player;

import ludo.board.PlayerColor;
import ludo.player.strategy.BlockingStrategy;

public class GreenPlayer extends Player {

    public GreenPlayer() {
        super(PlayerColor.GREEN, new BlockingStrategy(PlayerColor.GREEN));
    }
}
