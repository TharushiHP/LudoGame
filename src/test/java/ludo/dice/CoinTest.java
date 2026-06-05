package ludo.dice;

import ludo.piece.Direction;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CoinTest {

    @Test
    void tossReturnsClockwiseForHeads() {
        Coin coin = new Coin(bound -> 0);
        assertEquals(Direction.CLOCKWISE, coin.toss());
    }

    @Test
    void tossReturnsCounterClockwiseForTails() {
        Coin coin = new Coin(bound -> 1);
        assertEquals(Direction.COUNTER_CLOCKWISE, coin.toss());
    }
}
