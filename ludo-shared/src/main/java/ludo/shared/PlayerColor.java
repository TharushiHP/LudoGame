package ludo.shared;

/** The four players. Also identifies a player in snapshots and in every decision request. */
public enum PlayerColor {
    RED, GREEN, YELLOW, BLUE;

    public String display() {
        return name().charAt(0) + name().substring(1).toLowerCase();
    }
}
