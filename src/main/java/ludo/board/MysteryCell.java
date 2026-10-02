package ludo.board;

import ludo.dice.RandomSource;

import java.util.List;

/**
 * The mystery cell: where it is, how many rounds it stays, and when it relocates.
 * Queries are side-effect free (Command-Query Separation): {@link #isJustSpawned()} only reads
 * the "just spawned" flag and {@link #clearJustSpawned()} resets it.
 */
public class MysteryCell {

    private static final int NO_POSITION = -1;

    private int position;
    private int roundsRemaining;
    private int lastPosition;
    private boolean justSpawned;
    private final RandomSource random;

    public MysteryCell(RandomSource random) {
        this.random = random;
        this.position = NO_POSITION;
        this.roundsRemaining = 0;
        this.lastPosition = NO_POSITION;
        this.justSpawned = false;
    }

    public void trySpawn(List<Integer> occupiedCells) {
        if (isActive()) {
            roundsRemaining--;
            if (roundsRemaining == 0) {
                relocate(occupiedCells);
            }
            return;
        }
        spawn(occupiedCells);
    }

    private void spawn(List<Integer> occupiedCells) {
        int candidate = findFreeCell(occupiedCells);
        if (candidate != NO_POSITION) {
            position = candidate;
            roundsRemaining = BoardConstants.MYSTERY_DURATION_ROUNDS;
            justSpawned = true;
        }
    }

    private void relocate(List<Integer> occupiedCells) {
        lastPosition = position;
        position = NO_POSITION;
        spawn(occupiedCells);
    }

    private int findFreeCell(List<Integer> occupiedCells) {
        int attempts = 0;
        int maxAttempts = BoardConstants.MAIN_PATH_SIZE * 2;

        while (attempts < maxAttempts) {
            int candidate = random.nextInt(BoardConstants.MAIN_PATH_SIZE);
            boolean isFree = !occupiedCells.contains(candidate);
            boolean isNotLastPosition = candidate != lastPosition;
            if (isFree && isNotLastPosition) {
                return candidate;
            }
            attempts++;
        }
        return NO_POSITION;
    }

    public boolean isActive() {
        return position != NO_POSITION;
    }

    public boolean isAt(int cellPosition) {
        return position == cellPosition;
    }

    public int getPosition() {
        return position;
    }

    public int getRoundsRemaining() {
        return roundsRemaining;
    }

    public boolean isJustSpawned() {
        return justSpawned;
    }

    public void clearJustSpawned() {
        justSpawned = false;
    }

    public String positionLabel() {
        return isActive() ? String.valueOf(position) : "none";
    }
}
