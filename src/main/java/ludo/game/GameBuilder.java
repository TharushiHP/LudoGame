package ludo.game;

import ludo.board.Board;
import ludo.dice.Coin;
import ludo.dice.Dice;
import ludo.dice.RandomSource;

import java.util.Random;

/**
 * Builds a {@link Game} with its board, dice and coin sharing one random source
 * (Builder pattern). A fixed seed makes a game replay exactly.
 */
public class GameBuilder {

    private RandomSource randomSource;
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
        return new Game(board, dice, coin, maxRounds, stalemateRounds);
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
