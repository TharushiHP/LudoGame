package ludo.board;

public enum PlayerColor {
    RED, GREEN, YELLOW, BLUE;

    public String display() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
