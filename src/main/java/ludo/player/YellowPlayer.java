package ludo.player;

import ludo.board.PlayerColor;
import ludo.player.strategy.WinningStrategy;

public class YellowPlayer extends Player {

    public YellowPlayer() {
        super(PlayerColor.YELLOW, new WinningStrategy(PlayerColor.YELLOW));
    }
}
