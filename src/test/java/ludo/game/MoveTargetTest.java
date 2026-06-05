package ludo.game;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MoveTargetTest {

    // Main path

    @Test
    void mainPathTarget_isIdentifiedAsMainPathType() {
        MoveTarget target = MoveTarget.mainPath(10);
        assertTrue(target.isMainPath());
        assertFalse(target.isHomeStraight());
        assertFalse(target.isHome());
        assertFalse(target.isOvershoot());
    }

    @Test
    void mainPathTarget_storesGivenPosition() {
        assertEquals(10, MoveTarget.mainPath(10).getPosition());
    }

    // Home straight type

    @Test
    void homeStraightTarget_isIdentifiedAsHomeStraightType() {
        MoveTarget target = MoveTarget.homeStraight(3);
        assertFalse(target.isMainPath());
        assertTrue(target.isHomeStraight());
        assertFalse(target.isHome());
        assertFalse(target.isOvershoot());
    }

    @Test
    void homeStraightTarget_storesGivenIndex() {
        assertEquals(3, MoveTarget.homeStraight(3).getPosition());
    }

    @Test
    void homeStraightTarget_atIndexZero_isFirstCell() {
        assertEquals(0, MoveTarget.homeStraight(0).getPosition());
    }

    @Test
    void homeStraightTarget_atIndexFour_isLastCell() {
        assertEquals(4, MoveTarget.homeStraight(4).getPosition());
    }

    // Home type

    @Test
    void homeTarget_isIdentifiedAsHomeType() {
        MoveTarget target = MoveTarget.home();
        assertFalse(target.isMainPath());
        assertFalse(target.isHomeStraight());
        assertTrue(target.isHome());
        assertFalse(target.isOvershoot());
    }

    // Overshoot type

    @Test
    void overshootTarget_isIdentifiedAsOvershootType() {
        MoveTarget target = MoveTarget.overshoot();
        assertFalse(target.isMainPath());
        assertFalse(target.isHomeStraight());
        assertFalse(target.isHome());
        assertTrue(target.isOvershoot());
    }
}
