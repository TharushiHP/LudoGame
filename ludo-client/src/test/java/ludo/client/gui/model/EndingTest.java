package ludo.client.gui.model;

import ludo.shared.PlayerColor;
import ludo.shared.protocol.GameOverEvent;
import ludo.shared.snapshot.GameStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Which winner screen a GAME_OVER gets. */
class EndingTest {

    private static final PlayerColor R = PlayerColor.RED, G = PlayerColor.GREEN, Y = PlayerColor.YELLOW, B = PlayerColor.BLUE;

    @Test
    void finishedWithOnlyOnePlaceIsAFirstWinnerEnding() {
        Ending ending = Ending.of(new GameOverEvent(GameStatus.FINISHED, Map.of(R, 1, G, 0, Y, 0, B, 0)));
        assertTrue(ending.isFirstWinner());
        assertEquals(R, ending.winner());
    }

    @Test
    void allPlacesDecidedGetsThePodium() {
        Ending ending = Ending.of(new GameOverEvent(GameStatus.FINISHED, Map.of(R, 1, G, 3, Y, 2, B, 4)));
        assertEquals(Ending.Kind.PODIUM, ending.kind());
        assertNull(ending.winner());
    }

    @Test
    void otherEndingsGetThePodiumEvenWithOnePlace() {
        Map<PlayerColor, Integer> onePlace = Map.of(Y, 1, R, 0, G, 0, B, 0);
        for (GameStatus status : new GameStatus[]{GameStatus.STALEMATE, GameStatus.ROUND_CAP_REACHED, GameStatus.ABORTED})
            assertFalse(Ending.of(new GameOverEvent(status, onePlace)).isFirstWinner(), status.name());
        assertFalse(Ending.of(new GameOverEvent(GameStatus.ABORTED, Map.of())).isFirstWinner());
    }
}
