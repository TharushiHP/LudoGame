package ludo.effect;

import ludo.shared.BoardConstants;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EffectTest {

    @Test
    void energizedEffectDoublesMovement() {
        EnergizedEffect effect = new EnergizedEffect();
        assertEquals(8, effect.applyToSteps(4));
    }

    @Test
    void sickEffectHalvesMovement() {
        SickEffect effect = new SickEffect();
        assertEquals(2, effect.applyToSteps(4));
    }

    @Test
    void sickEffect_withOddDiceValue_halvesAndFloors() {
        SickEffect effect = new SickEffect();
        assertEquals(1, effect.applyToSteps(3));
    }

    @Test
    void briefingEffectBlocksMovement() {
        BriefingEffect effect = new BriefingEffect();
        assertEquals(0, effect.applyToSteps(6));
    }

    @Test
    void energizedEffectIsActiveAtStart() {
        EnergizedEffect effect = new EnergizedEffect();
        assertTrue(effect.isActive());
    }

    @Test
    void sickEffectIsActiveAtStart() {
        SickEffect effect = new SickEffect();
        assertTrue(effect.isActive());
    }

    @Test
    void effectBecomesInactiveAfterFourDecrements() {
        EnergizedEffect effect = new EnergizedEffect();
        for (int i = 0; i < BoardConstants.EFFECT_DURATION_ROUNDS; i++) {
            effect.decrementRound();
        }
        assertFalse(effect.isActive());
    }

    // Rule T-13: three consecutive 3s (not two) send the briefing piece to base.
    @Test
    void briefingEffectDetectsThreeConsecutiveThrees() {
        BriefingEffect effect = new BriefingEffect();
        effect.recordRoll(3);
        effect.recordRoll(3);
        assertFalse(effect.shouldTeleportToBase());
        effect.recordRoll(3);
        assertTrue(effect.shouldTeleportToBase());
    }

    @Test
    void briefingEffectResetsOnNonThree() {
        BriefingEffect effect = new BriefingEffect();
        effect.recordRoll(3);
        effect.recordRoll(4);
        effect.recordRoll(3);
        assertFalse(effect.shouldTeleportToBase());
    }

    @Test
    void energizedEffectDescriptionIsCorrect() {
        EnergizedEffect effect = new EnergizedEffect();
        assertTrue(effect.description().contains("doubles"));
    }

    @Test
    void sickEffectDescriptionIsCorrect() {
        SickEffect effect = new SickEffect();
        assertTrue(effect.description().contains("halves"));
    }

    @Test
    void briefingEffectDescriptionContainsBriefingInfo() {
        BriefingEffect effect = new BriefingEffect();
        assertTrue(effect.description().contains("briefing"));
    }
}
