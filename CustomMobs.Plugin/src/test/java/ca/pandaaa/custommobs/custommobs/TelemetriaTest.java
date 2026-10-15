package ca.pandaaa.custommobs.custommobs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;

import org.junit.jupiter.api.Test;

class TelemetriaTest {
    @Test
    void aRowHasOneValuePerHeaderColumnEvenWithCommasInTheExtra() {
        String riga = Telemetria.riga(1L, "colpo", "zombie_campione", new UUID(0, 1), "Kirmraft", "ENTITY_ATTACK",
                10, 3.456, 20, 12, 16, 20, 20, "NETHERITE_SWORD, sharp5");
        assertEquals(Telemetria.HEADER.split(",").length, riga.split(",", -1).length);
        assertTrue(riga.contains(",3.46,"));
    }

    @Test
    void fallDamageIsBlamedOnTheMobOnlyInsideTheWindow() {
        Telemetria.Contatto c = new Telemetria.Contatto("ragno_saltatore", new UUID(0, 2), 1_000);
        assertTrue(Telemetria.recente(c, 1_000 + Telemetria.FINESTRA_MS));
        assertFalse(Telemetria.recente(c, 1_001 + Telemetria.FINESTRA_MS));
        assertFalse(Telemetria.recente(null, 1_000));
    }
}
