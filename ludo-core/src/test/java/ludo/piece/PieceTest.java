package ludo.piece;

import ludo.shared.BoardConstants;
import ludo.shared.PlayerColor;
import ludo.effect.EnergizedEffect;
import ludo.effect.SickEffect;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PieceTest {

    private Piece piece;

    @BeforeEach
    void setUp() {
        piece = new Piece(PlayerColor.YELLOW, 1);
    }

    @Test
    void newPieceStartsAtBase() {
        assertTrue(piece.isAtBase());
        assertFalse(piece.isOnMainPath());
        assertFalse(piece.isHome());
    }

    @Test
    void placeOnStartMovesToMainPath() {
        piece.placeOnStart();
        assertTrue(piece.isOnMainPath());
        assertEquals(BoardConstants.YELLOW_START, piece.getMainPathPosition());
    }

    @Test
    void resetToBase_movesBackToBase() {
        piece.placeOnStart();
        piece.resetToBase();
        assertTrue(piece.isAtBase());
    }

    @Test
    void resetToBase_clearsCaptureCount() {
        piece.placeOnStart();
        piece.incrementCaptureCount();
        piece.resetToBase();
        assertEquals(0, piece.getCaptureCount());
    }

    @Test
    void resetToBase_clearsActiveEffect() {
        piece.placeOnStart();
        piece.setActiveEffect(new EnergizedEffect());
        piece.resetToBase();
        assertFalse(piece.hasEffect());
    }

    @Test
    void moveToHomePathSetsCorrectIndex() {
        piece.placeOnStart();
        piece.moveToHomePath(2);

        assertTrue(piece.isOnHomeStraight());
        assertEquals(2, piece.getHomePathIndex());
    }

    @Test
    void reachHomeMarksAsHome() {
        piece.placeOnStart();
        piece.reachHome();
        assertTrue(piece.isHome());
        assertFalse(piece.isActive());
    }

    // Effect application

    @Test
    void energizedEffectDoublesSteps() {
        piece.setActiveEffect(new EnergizedEffect());
        assertEquals(6, piece.applyEffect(3));
    }

    @Test
    void sickEffectHalvesSteps() {
        piece.setActiveEffect(new SickEffect());
        assertEquals(2, piece.applyEffect(4));
    }

    @Test
    void noEffectReturnsOriginalSteps() {
        assertEquals(5, piece.applyEffect(5));
    }

    @Test
    void sickEffect_withOddDiceValue_halvesAndFloors() {
        piece.setActiveEffect(new SickEffect());
        assertEquals(1, piece.applyEffect(3));
    }

    @Test
    void effectExpiresAfterFourRounds() {
        piece.setActiveEffect(new EnergizedEffect());
        for (int i = 0; i < BoardConstants.EFFECT_DURATION_ROUNDS; i++) {
            piece.decrementEffectRound();
        }
        assertFalse(piece.hasEffect());
    }

    @Test
    void applyEffect_afterEffectExpires_returnsRawDiceValue() {
        piece.setActiveEffect(new SickEffect());
        for (int i = 0; i < BoardConstants.EFFECT_DURATION_ROUNDS; i++) {
            piece.decrementEffectRound();
        }
        assertEquals(5, piece.applyEffect(5));
    }

    // Home straight entry rules

    @Test
    void cannotEnterHomeStraightWithoutCapture() {
        piece.placeOnStart();
        assertFalse(piece.canEnterHomeStraight());
    }

    @Test
    void canEnterHomeStraightAfterOneCapture() {
        piece.placeOnStart();
        piece.incrementCaptureCount();
        assertTrue(piece.canEnterHomeStraight());
    }

    // Position labels
    @Test
    void pieceNameIncludesColorInitialAndNumber() {
        assertEquals("Y1", piece.getName());
    }

    @Test
    void positionLabelShowsBaseWhenAtBase() {
        assertEquals("Base", piece.positionLabel());
    }

    @Test
    void positionLabelShowsCellNumberOnMainPath() {
        piece.placeOnStart();
        assertEquals(String.valueOf(BoardConstants.YELLOW_START), piece.positionLabel());
    }

    @Test
    void positionLabelShowsHomePathLabel() {
        piece.placeOnStart();
        piece.moveToHomePath(0);
        assertEquals("yellowhomepath0", piece.positionLabel());
    }

    @Test
    void yellowStartPositionIsZero() {
        assertEquals(0, piece.startPosition());
    }

    @Test
    void yellowApproachPositionIsCorrect() {
        assertEquals(BoardConstants.YELLOW_APPROACH, piece.approachPosition());
    }
}
