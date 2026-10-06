package ludo.players;

import ludo.shared.PlayerColor;

/**
 * Creates the behaviour for each colour (Factory pattern): Red aggressive, Green blocking,
 * Yellow winning/speedrunner, Blue cyclic. Replaces A1's PlayerFactory and the Red/Green/Yellow/Blue
 * Player subclasses, which existed only to attach a strategy to a player.
 */
public class StrategyFactory {

    public MoveStrategy create(PlayerColor color) {
        switch (color) {
            case RED:    return new AggressiveStrategy(color);
            case GREEN:  return new BlockingStrategy(color);
            case YELLOW: return new WinningStrategy(color);
            case BLUE:   return new CyclicStrategy(color);
            default:     throw new IllegalArgumentException("Unknown player color: " + color);
        }
    }
}
