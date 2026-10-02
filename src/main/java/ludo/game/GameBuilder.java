package ludo.game;

import ludo.board.Board;
import ludo.board.PlayerColor;
import ludo.dice.Coin;
import ludo.dice.Dice;
import ludo.dice.RandomSource;
import ludo.player.Player;
import ludo.player.PlayerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Builds a {@link Game} and wires every collaborator into it (Builder pattern; Dependency
 * Injection / composition root for the game): random source, board, dice, coin, the four
 * players, the {@link MoveDecider}, the {@link TurnGate} and the event listeners.
 * Defaults give the console game: {@link LocalStrategyDecider}, {@link NoOpTurnGate}, no listeners.
 * A fixed seed makes a game replay exactly.
 */
public class GameBuilder {

    private RandomSource randomSource;
    private MoveDecider moveDecider;
    private TurnGate turnGate = new NoOpTurnGate();
    private final List<GameEventListener> listeners = new ArrayList<>();
    private int maxRounds = Game.DEFAULT_MAX_ROUNDS;
    private int stalemateRounds = Game.DEFAULT_STALEMATE_ROUNDS;

    public GameBuilder withRandomSource(RandomSource source) {
        this.randomSource = source;
        return this;
    }

    public GameBuilder withSeed(long seed) {
        this.randomSource = new JavaRandom(seed);
        return this;
    }

    /** Replaces the default {@link LocalStrategyDecider}, e.g. with a remote-client decider. */
    public GameBuilder withMoveDecider(MoveDecider decider) {
        this.moveDecider = decider;
        return this;
    }

    public GameBuilder withTurnGate(TurnGate gate) {
        this.turnGate = gate;
        return this;
    }

    public GameBuilder withListener(GameEventListener listener) {
        this.listeners.add(listener);
        return this;
    }

    public GameBuilder withMaxRounds(int rounds) {
        this.maxRounds = rounds;
        return this;
    }

    public GameBuilder withStalemateRounds(int rounds) {
        this.stalemateRounds = rounds;
        return this;
    }

    public Game build() {
        RandomSource source = randomSource != null ? randomSource : new JavaRandom();
        Board board = new Board(source);
        Dice dice = new Dice(source);
        Coin coin = new Coin(source);
        List<Player> players = createPlayers();
        MoveDecider decider = moveDecider != null ? moveDecider : new LocalStrategyDecider(players, board);
        return new Game(board, dice, coin, players, decider, turnGate, listeners, maxRounds, stalemateRounds);
    }

    // The order Yellow, Blue, Red, Green is the A1 order used for the starting roll-off.
    private List<Player> createPlayers() {
        PlayerFactory factory = new PlayerFactory();
        return List.of(
                factory.create(PlayerColor.YELLOW),
                factory.create(PlayerColor.BLUE),
                factory.create(PlayerColor.RED),
                factory.create(PlayerColor.GREEN));
    }

    private static class JavaRandom implements RandomSource {
        private final Random random;

        JavaRandom() {
            this.random = new Random();
        }

        JavaRandom(long seed) {
            this.random = new Random(seed);
        }

        @Override
        public int nextInt(int bound) {
            return random.nextInt(bound);
        }
    }
}
