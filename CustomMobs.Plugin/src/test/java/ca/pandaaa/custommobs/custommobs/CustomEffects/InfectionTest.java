package ca.pandaaa.custommobs.custommobs.CustomEffects;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Infection lifecycle: one activation per damaging hit (melee or projectile, see EventsAttackerTest), no cooldown of its
 * own, no refresh and no stacking while the victim has the effect. PotionEffectType needs the server registry, so the
 * decisions are tested on their inputs.
 */
class InfectionTest {
    @Test
    void instantEffectActsOncePerActivation() {
        // A potion effect instance makes instant_damage/instant_health act on every tick it lasts:
        // 5 s gave ~4 magic hits in 2 s (the 10-tick hurt cooldown being the only limit).
        assertEquals(1, Infection.durationTicks(true, 5));
        assertEquals(1, Infection.durationTicks(true, 30));
    }

    @Test
    void instantEffectIsNeverInfinite() {
        assertEquals(1, Infection.durationTicks(true, -1));
    }

    @Test
    void durationEffectsKeepTheirConfiguredSeconds() {
        assertEquals(100, Infection.durationTicks(false, 5));
        assertEquals(600, Infection.durationTicks(false, 30));
        assertEquals(20, Infection.durationTicks(false, 1));
    }

    @Test
    void appliesOnlyWhenTheVictimDoesNotHaveTheEffect() {
        assertTrue(Infection.applies(false));
        assertFalse(Infection.applies(true), "no refresh of the duration, no stacking of the amplifier");
    }
}
