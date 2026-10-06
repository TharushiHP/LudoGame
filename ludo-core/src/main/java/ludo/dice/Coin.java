package ludo.dice;

import ludo.shared.Direction;

public class Coin {

    private static final int HEADS_THRESHOLD = 1;

    private final RandomSource random;

    public Coin(RandomSource random) {
        this.random = random;
    }

    public Direction toss() {
        int result = random.nextInt(2);
        return result < HEADS_THRESHOLD ? Direction.CLOCKWISE : Direction.COUNTER_CLOCKWISE;
    }
}
