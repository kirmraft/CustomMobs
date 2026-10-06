package ca.pandaaa.custommobs.custommobs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

/** Characterisation of today's behaviour (M15.2): documents it, does not endorse it. */
class AbilityCycleTest {

    @Test
    void cancelledActivationStillSpendsTheWholeCooldown() {
        assertEquals(10, AbilityCycle.secondsUntilNext(null, 10), "null = cancelled or nobody reached: activation lost");
    }

    @Test
    void successfulActivationsSpendTheCooldownToo() {
        assertEquals(10, AbilityCycle.secondsUntilNext(List.of(), 10));
        assertEquals(10, AbilityCycle.secondsUntilNext(List.of(Fakes.player("p")), 10));
    }
}
