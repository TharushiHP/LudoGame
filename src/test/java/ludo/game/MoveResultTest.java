package ludo.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoveResultTest {

    @Test
    void builderDefaultsToNoMove() {
        MoveResult result = MoveResult.builder().build();
        assertFalse(result.isMoved());
        assertFalse(result.isCaptured());
        assertFalse(result.isReachedHome());
        assertFalse(result.isLandedOnMystery());
    }

    @Test
    void builderSetsMovedFlag() {
        MoveResult result = MoveResult.builder().moved(true).build();
        assertTrue(result.isMoved());
    }

    @Test
    void builderSetsCapturedFlag() {
        MoveResult result = MoveResult.builder().captured(true).build();
        assertTrue(result.isCaptured());
    }

    @Test
    void builderSetsReachedHomeFlag() {
        MoveResult result = MoveResult.builder().reachedHome(true).build();
        assertTrue(result.isReachedHome());
    }

    @Test
    void builderSetsLandedOnMysteryFlag() {
        MoveResult result = MoveResult.builder().landedOnMystery(true).build();
        assertTrue(result.isLandedOnMystery());
    }

    @Test
    void builderSetsMessage() {
        MoveResult result = MoveResult.builder().message("test message").build();
        assertEquals("test message", result.getMessage());
    }

    @Test
    void builderChainsAllFields() {
        MoveResult result = MoveResult.builder()
                .moved(true)
                .captured(true)
                .reachedHome(false)
                .landedOnMystery(true)
                .message("chained")
                .build();

        assertTrue(result.isMoved());
        assertTrue(result.isCaptured());
        assertFalse(result.isReachedHome());
        assertTrue(result.isLandedOnMystery());
        assertEquals("chained", result.getMessage());
    }
}
