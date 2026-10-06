package ludo.client.gui.model;

import ludo.shared.BoardConstants;
import ludo.shared.Direction;
import ludo.shared.EffectKind;
import ludo.shared.PieceLocation;
import ludo.shared.PlayerColor;
import ludo.shared.snapshot.MysterySnapshot;
import ludo.shared.snapshot.PieceSnapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Hover texts of tokens and cells. */
class TokenTextTest {

    @Test
    void tokenShowsNameLocationDirectionCapturesAndEffect() {
        PieceSnapshot r1 = new PieceSnapshot(PlayerColor.RED, 1, PieceLocation.MAIN_PATH, BoardConstants.BETA,
                Direction.CLOCKWISE, 2, EffectKind.ENERGIZED, 3, true);
        assertEquals("R1 (Red) · cell 25 (Beta) · clockwise · 2 captures · energised, 3 rounds left · in a block",
                TokenText.piece(r1));
        PieceSnapshot b2 = new PieceSnapshot(PlayerColor.BLUE, 2, PieceLocation.BASE, -1, Direction.CLOCKWISE, 0, EffectKind.NONE, 0, false);
        assertEquals("B2 (Blue) · in base · no captures yet (cannot enter its home straight)", TokenText.piece(b2));
        PieceSnapshot g3 = new PieceSnapshot(PlayerColor.GREEN, 3, PieceLocation.HOME_STRAIGHT, 2, Direction.COUNTER_CLOCKWISE, 1, EffectKind.NONE, 0, false);
        assertEquals("G3 (Green) · home straight, square 3 of 5 · counterclockwise · 1 capture", TokenText.piece(g3));
    }

    @Test
    void cellsNameTheirSpecialMeaningFromTheRules() {
        assertEquals("Cell 13 · Blue start (X)", TokenText.cell(13, null));
        assertEquals("Cell 50 · Yellow approach (turn into the home straight)", TokenText.cell(50, null));
        assertTrue(TokenText.cell(BoardConstants.ALPHA, null).contains("Alpha (coin toss: energised or sick)"));
        assertTrue(TokenText.cell(BoardConstants.GAMMA, null).contains("Gamma"));
        assertEquals("Cell 30 · mystery cell, 2 rounds left", TokenText.cell(30, new MysterySnapshot(30, 2)));
        assertEquals("Cell 31", TokenText.cell(31, new MysterySnapshot(30, 2)));
        assertEquals("β", TokenText.greek(BoardConstants.BETA));
        assertEquals("", TokenText.greek(3));
    }
}
