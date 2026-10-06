package ca.pandaaa.custommobs.custommobs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;

import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.junit.jupiter.api.Test;

class SpawnRulesTest {

    @Test
    void onlyNaturalAndChunkGenAreReplaceable() {
        for (SpawnReason reason : SpawnReason.values()) {
            boolean expected = reason == SpawnReason.NATURAL || reason == SpawnReason.CHUNK_GEN;
            assertEquals(expected, SpawnRules.replaceable(reason), reason.name());
        }
    }

    @Test
    void farmableAndArtificialReasonsAreExplicitlyExcluded() {
        // Any spawner plugin (RoseStacker stacked spawners included) spawns with SPAWNER: the reason is enough.
        for (SpawnReason reason : EnumSet.of(SpawnReason.SPAWNER, SpawnReason.TRIAL_SPAWNER, SpawnReason.SPAWNER_EGG,
                SpawnReason.DISPENSE_EGG, SpawnReason.COMMAND, SpawnReason.CUSTOM, SpawnReason.BREEDING,
                SpawnReason.SLIME_SPLIT, SpawnReason.REINFORCEMENTS, SpawnReason.BUILD_IRONGOLEM,
                SpawnReason.LIGHTNING, SpawnReason.DROWNED, SpawnReason.INFECTION, SpawnReason.DEFAULT)) {
            assertFalse(SpawnRules.replaceable(reason), reason.name());
        }
    }
}
