package ludo.client.gui.model;

import ludo.shared.PieceLocation;
import ludo.shared.snapshot.PieceSnapshot;

/**
 * One place a token can stand on (Value Object): a main-path cell, a square of its own home
 * straight, Home, or its slot in the base. {@code position} is the cell (0-51) or home-straight
 * index (0-4), and -1 for Home and base.
 */
public record Place(PieceLocation location, int position) {

    public static Place of(PieceSnapshot piece) {
        return new Place(piece.location(), piece.isActive() ? piece.position() : -1);
    }

    public static Place cell(int cell) {
        return new Place(PieceLocation.MAIN_PATH, cell);
    }

    public static Place homeStraight(int index) {
        return new Place(PieceLocation.HOME_STRAIGHT, index);
    }

    public static final Place HOME = new Place(PieceLocation.HOME, -1);
    public static final Place BASE = new Place(PieceLocation.BASE, -1);
}
