package ludo.client.gui.model;

import ludo.shared.PlayerColor;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.PieceSnapshot;

import java.util.ArrayList;
import java.util.List;

/**
 * A token that stands somewhere else in the new snapshot than in the old one (Value Object), and
 * {@link #between} which finds them all. Only the place counts: a new effect or direction alone
 * is not a move.
 */
public record PieceChange(PieceSnapshot before, PieceSnapshot after) {

    public PlayerColor colour() {
        return after.color();
    }

    public int number() {
        return after.number();
    }

    /** Every token whose place differs between the two snapshots, in snapshot order. */
    public static List<PieceChange> between(GameSnapshot before, GameSnapshot after) {
        List<PieceChange> changes = new ArrayList<>();
        if (before == null || after == null)
            return changes;
        for (PieceSnapshot now : after.pieces()) {
            for (PieceSnapshot then : before.pieces()) {
                if (then.color() == now.color() && then.number() == now.number()) {
                    if (!Place.of(then).equals(Place.of(now)))
                        changes.add(new PieceChange(then, now));
                    break;
                }
            }
        }
        return changes;
    }
}
