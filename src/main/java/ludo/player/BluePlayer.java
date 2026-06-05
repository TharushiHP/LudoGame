package ludo.player;

import ludo.board.PlayerColor;
import ludo.player.strategy.CyclicStrategy;

public class BluePlayer extends Player {

    public BluePlayer() {
        super(PlayerColor.BLUE, new CyclicStrategy(PlayerColor.BLUE));
    }
}
