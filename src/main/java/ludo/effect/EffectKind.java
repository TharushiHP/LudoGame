package ludo.effect;

/**
 * Names the kind of effect on a piece, so snapshots can describe an effect without
 * {@code instanceof} checks (Open/Closed: a new effect only adds a constant).
 */
public enum EffectKind {
    NONE,
    ENERGIZED,
    SICK,
    BRIEFING
}
