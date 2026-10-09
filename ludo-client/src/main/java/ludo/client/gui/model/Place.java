package ludo.client.gui.model;

import ludo.shared.PieceLocation;
import ludo.shared.snapshot.PieceSnapshot;


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
