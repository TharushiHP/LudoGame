package ludo.dice;

public class Dice {

    private static final int SIDES = 6;
    private static final int MIN_VALUE = 1;

    private final RandomSource random;

    public Dice(RandomSource random) {
        this.random = random;
    }

    public int roll() {
        return random.nextInt(SIDES) + MIN_VALUE;
    }
}
