package ludo.game;

import ludo.players.SnapshotStrategyDecider;
import ludo.shared.PlayerColor;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Rule 11 end conditions: stop at the first winner, or play on for every place (the default). */
class EndConditionTest {

    private static final long SEED = 7; // Red finishes first, then Green, Yellow, Blue (golden file seed-7.txt)
    private static final String FIRST_WINNER_LINE =
            "\nThe game ends with the first winner (Rule 11): the other players are not ranked.";

    private record Played(GameSnapshot end, List<String> log) {
    }

    private static Played play(GameBuilder builder) {
        List<String> log = new ArrayList<>();
        Game game = builder.withSeed(SEED).withMoveDecider(new SnapshotStrategyDecider())
                .withListener((event, message) -> log.add(message)).build();
        game.run();
        return new Played(game.snapshot(), log);
    }

    @Test
    void firstWinnerStopsAsSoonAsOnePlayerHasAllPiecesHome() {
        Played first = play(new GameBuilder().withEndCondition(EndCondition.FIRST_WINNER));
        Played all = play(new GameBuilder().withEndCondition(EndCondition.ALL_PLACES));

        assertEquals(GameStatus.FINISHED, first.end().status());
        Map<PlayerColor, Integer> places = first.end().finishPositions();
        assertEquals(1, places.get(PlayerColor.RED), "Red is the first to bring all four pieces Home");
        assertEquals(0, places.get(PlayerColor.GREEN));
        assertEquals(0, places.get(PlayerColor.YELLOW));
        assertEquals(0, places.get(PlayerColor.BLUE));
        assertTrue(first.end().turnCount() < all.end().turnCount(), "the game stops early");

        List<String> log = first.log();
        int wins = log.indexOf("Red player wins!!!");
        assertTrue(wins >= 0, "the winner line is printed as before");
        assertTrue(log.indexOf(FIRST_WINNER_LINE) > wins, "then one line says the game ended at the first winner");
        assertTrue(log.contains("1st place: Red player wins!!!"));
        assertTrue(log.contains("Not ranked: Green player"));
        assertEquals(1, log.stream().filter(line -> line.endsWith("player wins!!!") && !line.contains("place")).count(),
                "nobody else finishes");
    }

    @Test
    void allPlacesPlaysOnUntilEveryPlayerIsPlacedAsBefore() {
        Played all = play(new GameBuilder().withEndCondition(EndCondition.ALL_PLACES));
        assertEquals(GameStatus.FINISHED, all.end().status());
        assertEquals(Map.of(PlayerColor.RED, 1, PlayerColor.GREEN, 2, PlayerColor.YELLOW, 3, PlayerColor.BLUE, 4),
                all.end().finishPositions());
        assertFalse(all.log().contains(FIRST_WINNER_LINE));
    }

    @Test
    void theDefaultIsAllPlacesSoTheConsoleGameIsUnchanged() {
        Played byDefault = play(new GameBuilder());
        Played all = play(new GameBuilder().withEndCondition(EndCondition.ALL_PLACES));
        assertEquals(all.log(), byDefault.log());
        assertEquals(all.end(), byDefault.end());
    }
}
