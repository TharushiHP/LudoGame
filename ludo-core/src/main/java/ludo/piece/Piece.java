package ludo.piece;

import ludo.shared.PieceLocation;
import ludo.shared.Direction;
import ludo.shared.PathMath;
import ludo.shared.PlayerColor;
import ludo.effect.PieceEffect;

/**
 * A single piece: where it is, which way it moves, its captures and any active effect
 * (entity; holds an effect via the {@link PieceEffect} Strategy). Start and approach cells
 * come from {@link PathMath}, shared with the client-side strategies.
 */
public class Piece {

    private final PlayerColor color;
    private final int pieceNumber;

    private PieceLocation location;
    private int mainPathPosition;
    private int homePathIndex;
    private Direction direction;

    private int captureCount;
    private int approachPassCount;
    private PieceEffect activeEffect;

    public Piece(PlayerColor color, int pieceNumber) {
        this.color = color;
        this.pieceNumber = pieceNumber;
        resetToBase();
    }

    public void resetToBase() {
        this.location = PieceLocation.BASE;
        this.mainPathPosition = -1;
        this.homePathIndex = -1;
        this.direction = Direction.CLOCKWISE;
        this.captureCount = 0;
        this.approachPassCount = 0;
        this.activeEffect = null;
    }

    public void placeOnStart() {
        this.location = PieceLocation.MAIN_PATH;
        this.mainPathPosition = startPosition();
        this.homePathIndex = -1;
    }

    public void moveToHomePath(int index) {
        this.location = PieceLocation.HOME_STRAIGHT;
        this.homePathIndex = index;
        this.mainPathPosition = -1;
    }

    public void reachHome() {
        this.location = PieceLocation.HOME;
        this.mainPathPosition = -1;
        this.homePathIndex = -1;
    }

    public void setMainPathPosition(int position) {
        this.mainPathPosition = position;
    }

    public void setDirection(Direction direction) {
        this.direction = direction;
    }

    public void setActiveEffect(PieceEffect effect) {
        this.activeEffect = effect;
    }

    public void clearEffect() {
        this.activeEffect = null;
    }

    public void incrementCaptureCount() {
        this.captureCount++;
    }

    public void incrementApproachPassCount() {
        this.approachPassCount++;
    }

    public void decrementEffectRound() {
        if (activeEffect != null) {
            activeEffect.decrementRound();
            if (!activeEffect.isActive()) {
                activeEffect = null;
            }
        }
    }

    public int applyEffect(int diceValue) {
        if (activeEffect == null) {
            return diceValue;
        }
        return activeEffect.applyToSteps(diceValue);
    }

    public int startPosition() {
        return PathMath.startCell(color);
    }

    public int approachPosition() {
        return PathMath.approachCell(color);
    }

    public boolean isAtBase()         { return location == PieceLocation.BASE; }
    public boolean isOnMainPath()     { return location == PieceLocation.MAIN_PATH; }
    public boolean isOnHomeStraight() { return location == PieceLocation.HOME_STRAIGHT; }
    public boolean isHome()           { return location == PieceLocation.HOME; }
    public boolean isActive()         { return !isAtBase() && !isHome(); }
    public boolean hasCapture()       { return captureCount > 0; }
    public boolean hasEffect()        { return activeEffect != null; }

    public boolean canEnterHomeStraight() {
        if (direction == Direction.CLOCKWISE) {
            return captureCount >= 1;
        }
        return captureCount >= 1 && approachPassCount >= 2;
    }

    /**
     * T-7 check made before a move, without side effects: may this piece enter its home
     * straight the next time it passes its approach cell? Counterclockwise pieces count
     * that pass as well (the move itself increments the pass count).
     */
    public boolean canEnterHomeStraightOnNextPass() {
        if (direction == Direction.CLOCKWISE) {
            return captureCount >= 1;
        }
        return captureCount >= 1 && approachPassCount + 1 >= 2;
    }

    public PlayerColor getColor()       { return color; }
    public int getPieceNumber()         { return pieceNumber; }
    public PieceLocation getLocation()  { return location; }
    public int getMainPathPosition()    { return mainPathPosition; }
    public int getHomePathIndex()       { return homePathIndex; }
    public Direction getDirection()     { return direction; }
    public int getCaptureCount()        { return captureCount; }
    public int getApproachPassCount()   { return approachPassCount; }
    public PieceEffect getActiveEffect(){ return activeEffect; }

    public String getName() {
        return color.name().charAt(0) + String.valueOf(pieceNumber);
    }

    public String positionLabel() {
        if (isAtBase())         return "Base";
        if (isHome())           return "Home";
        if (isOnHomeStraight()) return color.display().toLowerCase() + "homepath" + homePathIndex;
        return String.valueOf(mainPathPosition);
    }

    @Override
    public String toString() {
        return getName() + " at " + positionLabel();
    }
}
