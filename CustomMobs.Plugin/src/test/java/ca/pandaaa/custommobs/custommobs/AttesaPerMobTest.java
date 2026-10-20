package ca.pandaaa.custommobs.custommobs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Wait shared by Charge, Infection (zombie_corrotto wither) and MobSpawner (prima_strega): per mob, 0 = no wait. */
class AttesaPerMobTest {
    private final java.util.function.LongSupplier original = AttesaPerMob.clock;
    private long now = 1_000_000;

    AttesaPerMobTest() {
        AttesaPerMob.clock = () -> now;
    }

    @AfterEach
    void restore() {
        AttesaPerMob.clock = original;
    }

    @Test
    void aMobWaitsTheConfiguredSecondsThenIsReadyAgain() {
        AttesaPerMob attesa = new AttesaPerMob();
        UUID mob = new UUID(0, 1);
        attesa.avvia(mob, 120);
        now += 119_999;
        assertFalse(attesa.pronto(mob));
        now += 1;
        assertTrue(attesa.pronto(mob));
    }

    @Test
    void zeroSecondsKeepsTheOldBehaviour() {
        AttesaPerMob attesa = new AttesaPerMob();
        UUID mob = new UUID(0, 1);
        attesa.avvia(mob, 0);
        assertTrue(attesa.pronto(mob));
    }

    @Test
    void anotherMobOfTheSameTypeIsNotBlocked() {
        AttesaPerMob attesa = new AttesaPerMob();
        attesa.avvia(new UUID(0, 1), 60);
        assertTrue(attesa.pronto(new UUID(0, 2)));
    }
}
