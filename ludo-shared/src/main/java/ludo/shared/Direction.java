package ludo.shared;

/** Direction a piece travels round the main path, decided by the coin toss when it leaves base (T-1). */
public enum Direction {
    CLOCKWISE, COUNTER_CLOCKWISE;

    public Direction opposite() {
        return this == CLOCKWISE ? COUNTER_CLOCKWISE : CLOCKWISE;
    }

    public String display() {
        return this == CLOCKWISE ? "clockwise" : "counterclockwise";
    }
}
