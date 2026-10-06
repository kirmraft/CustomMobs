package ca.pandaaa.custommobs.custommobs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.NamespacedKey;
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
        for (SpawnReason reason : EnumSet.of(SpawnReason.SPAWNER, SpawnReason.TRIAL_SPAWNER, SpawnReason.SPAWNER_EGG,
                SpawnReason.DISPENSE_EGG, SpawnReason.COMMAND, SpawnReason.CUSTOM, SpawnReason.BREEDING,
                SpawnReason.SLIME_SPLIT, SpawnReason.REINFORCEMENTS, SpawnReason.BUILD_IRONGOLEM,
                SpawnReason.LIGHTNING, SpawnReason.DROWNED, SpawnReason.INFECTION, SpawnReason.DEFAULT)) {
            assertFalse(SpawnRules.replaceable(reason), reason.name());
        }
    }

    @Test
    void pyroSpawnersMarksMeanForeignSpawner() {
        assertTrue(SpawnRules.fromForeignSpawner(Set.of(new NamespacedKey("pyrospawners", "stacksize"))));
        assertTrue(SpawnRules.fromForeignSpawner(Set.of(new NamespacedKey("pyrospawners", "vanilla"))));
    }

    @Test
    void roseStackerSpawnerMarksMeanForeignSpawner() {
        assertTrue(SpawnRules.fromForeignSpawner(Set.of(new NamespacedKey("rosestacker", "spawner_spawned"))));
        assertTrue(SpawnRules.fromForeignSpawner(Set.of(new NamespacedKey("rosestacker", "trial_spawner_spawned"))));
    }

    @Test
    void otherKeysAreNotSpawnerMarks() {
        assertFalse(SpawnRules.fromForeignSpawner(Set.of()));
        assertFalse(SpawnRules.fromForeignSpawner(Set.of(new NamespacedKey("rosestacker", "unstackable"),
                new NamespacedKey("levelledmobs", "level"))));
    }
}
