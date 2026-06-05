package ludo.player;

import ludo.board.PlayerColor;

public class PlayerFactory {

    public Player create(PlayerColor color) {
        switch (color) {
            case RED:    return new RedPlayer();
            case GREEN:  return new GreenPlayer();
            case YELLOW: return new YellowPlayer();
            case BLUE:   return new BluePlayer();
            default:     throw new IllegalArgumentException("Unknown player color: " + color);
        }
    }
}
