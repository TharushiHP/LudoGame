package ludo.game;

import ludo.board.PlayerColor;
import ludo.effect.EffectKind;
import ludo.effect.PieceEffect;
import ludo.piece.Direction;
import ludo.piece.Piece;
import ludo.piece.PieceLocation;

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

    static PieceSnapshot of(Piece piece, boolean inBlock) {
        int position = piece.isOnMainPath() ? piece.getMainPathPosition()
                : piece.isOnHomeStraight() ? piece.getHomePathIndex()
                : -1;
        PieceEffect effect = piece.getActiveEffect();
        return new PieceSnapshot(piece.getColor(), piece.getPieceNumber(), piece.getLocation(), position,
                piece.getDirection(), piece.getCaptureCount(),
                effect == null ? EffectKind.NONE : effect.kind(),
                effect == null ? 0 : effect.getRoundsRemaining(),
                inBlock);
    }
}
