package ludo.board;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MysteryCellTest {

    // Command-Query Separation: reading the flag must not clear it.
    @Test
    void isJustSpawnedOnlyReadsTheFlag() {
        MysteryCell cell = new MysteryCell(bound -> 5);
        cell.trySpawn(List.of());

        assertTrue(cell.isJustSpawned());
        assertTrue(cell.isJustSpawned());

        cell.clearJustSpawned();
        assertFalse(cell.isJustSpawned());
    }
}
