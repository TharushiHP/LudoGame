package ludo.effect;

/**
 * A temporary effect on a piece from the mystery cell. Each implementation changes movement
 * in its own way (Strategy pattern; new effects extend the game without editing Piece, OCP).
 */
public interface PieceEffect {

    int applyToSteps(int diceValue);

    boolean isActive();

    void decrementRound();

    String description();

    EffectKind kind();

    int getRoundsRemaining();
}
