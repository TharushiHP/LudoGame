package ludo.dice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.*;

class DiceTest {

    @Test
    void rollReturnsValueWithinOnToSix() {
        Dice dice = new Dice(bound -> 3);
        int result = dice.roll();
        assertTrue(result >= 1 && result <= 6);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2, 3, 4, 5})
    void rollMapsRandomOutputToFaceValue(int randomValue) {
        Dice dice = new Dice(bound -> randomValue);
        int result = dice.roll();
        assertEquals(randomValue + 1, result);
    }

    @Test
    void rollReturnsSixWhenRandomReturnsMaxIndex() {
        Dice dice = new Dice(bound -> 5);
        assertEquals(6, dice.roll());
    }

    @Test
    void rollReturnsOneWhenRandomReturnsZero() {
        Dice dice = new Dice(bound -> 0);
        assertEquals(1, dice.roll());
    }
}
