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

    public GameBuilder withRandomSource(RandomSource source) {
        this.randomSource = source;
        return this;
    }

    public GameBuilder withSeed(long seed) {
        this.randomSource = new JavaRandom(seed);
        return this;
    }

    public Game build() {
        RandomSource source = randomSource != null ? randomSource : new JavaRandom();
        Board board = new Board(source);
        Dice dice = new Dice(source);
        Coin coin = new Coin(source);
        return new Game(board, dice, coin);
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
