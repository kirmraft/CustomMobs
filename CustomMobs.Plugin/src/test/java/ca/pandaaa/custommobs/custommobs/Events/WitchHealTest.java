package ca.pandaaa.custommobs.custommobs.Events;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/** megera (104 HP) spent the fight drinking: a CustomMob witch heals only below half health. */
class WitchHealTest {
    @Test
    void healingAboveHalfHealthIsRefused() {
        assertTrue(Events.troppoPresto(true, 90, 104));
        assertTrue(Events.troppoPresto(true, 53, 104));
    }

    @Test
    void healingAtOrBelowHalfHealthIsAllowed() {
        assertFalse(Events.troppoPresto(true, 52, 104));
        assertFalse(Events.troppoPresto(true, 10, 104));
    }

    @Test
    void otherPotionsAreNeverRefused() {
        assertFalse(Events.troppoPresto(false, 104, 104));
    }
}
