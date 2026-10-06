package ludo.client.gui.model;

import ludo.shared.PlayerColor;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DiceFacesTest {

    @Test
    void eachFaceHasThatManyDistinctPipsInsideTheFace() {
        for (int value = 1; value <= 6; value++) {
            List<DiceFaces.Pip> pips = DiceFaces.pips(value);
            assertEquals(value, pips.size());
            assertEquals(value, new HashSet<>(pips).size(), "pips of " + value + " overlap");
            for (DiceFaces.Pip p : pips)
                assertTrue(p.x() > 0 && p.x() < 1 && p.y() > 0 && p.y() < 1);
        }
        assertThrows(IllegalArgumentException.class, () -> DiceFaces.pips(7));
    }

    @Test
    void aTumbleEndsOnTheRollAndChangesFaceEveryFrame() {
        for (int value = 1; value <= 6; value++) {
            int[] faces = DiceFaces.tumble(value, 6, 42 + value);
            assertEquals(6, faces.length);
            assertEquals(value, faces[faces.length - 1]);
            for (int i = 1; i < faces.length; i++) {
                assertNotEquals(faces[i - 1], faces[i], "same face twice in a row");
                assertTrue(faces[i] >= 1 && faces[i] <= 6);
            }
        }
        assertArrayEquals(DiceFaces.tumble(4, 6, 7), DiceFaces.tumble(4, 6, 7), "same seed, same tumble");
        assertArrayEquals(new int[]{5}, DiceFaces.tumble(5, 1, 1));
    }

    @Test
    void rollsAreReadFromTheLog() {
        assertEquals(Map.of(PlayerColor.RED, 4), DiceFaces.rollsIn(List.of("Red player rolled 4.", "Red moves piece R1 ...")));
        Map<PlayerColor, Integer> start = DiceFaces.rollsIn(List.of("Yellow rolls 5", "Blue rolls 2", "Red rolls 6", "Green rolls 1",
                "Red player has the highest roll and will begin the game."));
        assertEquals(List.of(PlayerColor.YELLOW, PlayerColor.BLUE, PlayerColor.RED, PlayerColor.GREEN), List.copyOf(start.keySet()));
        assertEquals(6, start.get(PlayerColor.RED));
        assertTrue(DiceFaces.rollsIn(List.of("Red rolled six three times consecutively. Turn passed.")).isEmpty());
    }
}
