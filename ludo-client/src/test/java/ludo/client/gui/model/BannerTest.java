package ludo.client.gui.model;

import ludo.shared.PlayerColor;
import ludo.shared.snapshot.GameSnapshot;
import ludo.shared.snapshot.GameStatus;
import ludo.shared.snapshot.MysterySnapshot;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/** Banners come from the game's own log lines (copied from the golden files). */
class BannerTest {

    private static Banner one(String line) {
        List<Banner> banners = Banner.fromLog(List.of(line), null);
        assertEquals(1, banners.size(), "one banner for: " + line);
        return banners.get(0);
    }

    @Test
    void capture() {
        assertEquals(new Banner(Banner.Kind.CAPTURE, "R2 captures B1!", PlayerColor.RED),
                one("Red piece R2 lands on square 27, captures Blue piece B1, and returns it to the base."));
        assertEquals(new Banner(Banner.Kind.CAPTURE, "Blue block captures 2 pieces!", PlayerColor.BLUE),
                one("Blue block lands on square 27 and captures 2 opponent piece(s)."));
    }

    @Test
    void mysteryTeleportAndEffects() {
        assertEquals(new Banner(Banner.Kind.MYSTERY, "Mystery cell! B3 teleports to Beta", PlayerColor.BLUE),
                one("Blue piece B3 teleported to Beta."));
        assertEquals(Banner.Kind.EFFECT, one("Yellow piece Y2 feels energized, and movement speed doubles.").kind());
        assertEquals("Y2 is sick: half speed", one("Yellow piece Y2 feels sick, and movement speed halves.").text());
        assertEquals("B3 is in a briefing for 4 rounds", one("Blue piece B3 attends briefing and cannot move for four rounds.").text());
        assertEquals("R2 turns round: now counterclockwise",
                one("The Red piece R2, which was moving clockwise, has changed to moving counterclockwise.").text());
    }

    @Test
    void blockHomeAndFinish() {
        assertEquals(new Banner(Banner.Kind.BLOCK, "Blue forms a block on cell 20", PlayerColor.BLUE),
                one("Blue piece B4 forms a block at cell 20."));
        assertEquals(new Banner(Banner.Kind.HOME, "Y2 reached Home!", PlayerColor.YELLOW),
                one("Yellow piece Y2 has reached Home!"));
        assertEquals("Blue takes 4th place", one("Blue player is the only player left and takes 4th place.").text());
    }

    @Test
    void aFinishingPlayerGetsItsPlaceFromTheSnapshot() {
        GameSnapshot snapshot = new GameSnapshot(50, 200, PlayerColor.RED, 3, new MysterySnapshot(-1, 0),
                Map.of(PlayerColor.RED, 1), GameStatus.IN_PROGRESS, List.of(), Map.of());
        assertEquals(List.of(new Banner(Banner.Kind.FINISH, "Red finishes 1st!", PlayerColor.RED)),
                Banner.fromLog(List.of("Red player wins!!!"), snapshot));
    }

    @Test
    void ordinaryLinesGiveNoBanner() {
        assertTrue(Banner.fromLog(List.of(
                "Red player rolled 4.",
                "Red moves piece R1 from location 3 to 7 by 4 units in clockwise direction.",
                "Piece R1 -> 7",
                "The mystery cell is at 30 and will be at that location for the next 2 rounds.",
                "1st place: Red player wins!!!", // final summary: the winner screen shows it
                "The Blue piece B3 is moving in a counterclockwise direction. Teleporting to Beta from Gamma.",
                ""), null).isEmpty());
    }

    @Test
    void atMostThreePerStateAndNoRepeats() {
        List<Banner> banners = Banner.fromLog(List.of(
                "Blue piece B3 teleported to Gamma.",
                "Blue piece B3 teleported to Beta.",
                "Blue piece B3 teleported to Beta.",
                "Blue piece B3 attends briefing and cannot move for four rounds.",
                "Yellow piece Y2 has reached Home!"), null);
        assertEquals(3, banners.size());
        assertEquals("Mystery cell! B3 teleports to Gamma", banners.get(0).text());
        assertEquals("Mystery cell! B3 teleports to Beta", banners.get(1).text());
        assertEquals(Banner.Kind.EFFECT, banners.get(2).kind());
    }
}
