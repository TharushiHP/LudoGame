package ludo.game;

import ludo.board.Board;
import ludo.dice.Coin;
import ludo.dice.Dice;
import ludo.dice.RandomSource;

import java.util.Random;

public class GameBuilder {

    private RandomSource randomSource;

    public GameBuilder withRandomSource(RandomSource source) {
        this.randomSource = source;
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
        private final Random random = new Random();

        @Override
        public int nextInt(int bound) {
            return random.nextInt(bound);
        }
    }
}
