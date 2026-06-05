package ludo.piece;

public enum Direction {
    CLOCKWISE, COUNTER_CLOCKWISE;

    public Direction opposite() {
        return this == CLOCKWISE ? COUNTER_CLOCKWISE : CLOCKWISE;
    }

    public String display() {
        return this == CLOCKWISE ? "clockwise" : "counterclockwise";
    }
}
