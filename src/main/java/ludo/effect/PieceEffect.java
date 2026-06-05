package ludo.effect;

public interface PieceEffect {

    int applyToSteps(int diceValue);

    boolean isActive();

    void decrementRound();

    String description();
}
