package ludo.client.control;

import ludo.shared.PlayerColor;

public record Identity(PlayerColor colour, String name) {

    public static Identity player(PlayerColor colour, String name) {
        return new Identity(colour, name == null || name.isBlank() ? "Player " + colour.display() : name);
    }

    public static Identity spectator(String name) {
        return new Identity(null, name == null || name.isBlank() ? "Spectator" : name);
    }

    public boolean isSpectator() {
        return colour == null;
    }

    /** True if this client plays {@code other}. */
    public boolean plays(PlayerColor other) {
        return colour != null && colour == other;
    }

    /** "You are Player A (Red)" or "Spectator". */
    public String headline() {
        return isSpectator() ? "Spectator: " + name : "You are " + name + " (" + colour.display() + ")";
    }
}
