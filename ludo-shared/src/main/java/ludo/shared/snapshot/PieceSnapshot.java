package ludo.shared.snapshot;

import ludo.shared.decision.MoveDecider;
import ludo.shared.EffectKind;
import ludo.shared.PieceLocation;
import ludo.shared.Direction;
import ludo.shared.PlayerColor;

/**
 * Immutable copy of one piece's state at a moment in time (Value Object, Java record).
 *
 * @param number           piece number 1-4 within its colour; this is the piece ID used by {@link MoveDecider}
 * @param position         main-path cell when on MAIN_PATH, home-straight index when on HOME_STRAIGHT, otherwise -1
 * @param effectRoundsLeft rounds left on the active effect, 0 when {@code effect} is NONE
 * @param inBlock          true when two or more pieces of this colour share its main-path cell
 */
public record PieceSnapshot(PlayerColor color,
                            int number,
                            PieceLocation location,
                            int position,
                            Direction direction,
                            int captureCount,
                            EffectKind effect,
                            int effectRoundsLeft,
                            boolean inBlock) {

    public boolean isAtBase()         { return location == PieceLocation.BASE; }
    public boolean isOnMainPath()     { return location == PieceLocation.MAIN_PATH; }
    public boolean isOnHomeStraight() { return location == PieceLocation.HOME_STRAIGHT; }
    public boolean isHome()           { return location == PieceLocation.HOME; }
    public boolean isActive()         { return isOnMainPath() || isOnHomeStraight(); }
    public boolean hasCapture()       { return captureCount > 0; }
}
