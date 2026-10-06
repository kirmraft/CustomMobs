package ca.pandaaa.custommobs.custommobs;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.NamespacedKey;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/**
 * Which vanilla spawns the natural replacement may turn into a CustomMob.
 * Whitelist: only real world spawns. Spawners, eggs, commands, breeding, splits, conversions,
 * reinforcements and every other reason (including future ones) are excluded.
 */
public final class SpawnRules {
    private static final Set<SpawnReason> REPLACEABLE = EnumSet.of(SpawnReason.NATURAL, SpawnReason.CHUNK_GEN);

    private SpawnRules() {
    }

    public static boolean replaceable(SpawnReason reason) {
        return REPLACEABLE.contains(reason);
    }

    /**
     * True when another plugin marked the entity as born from its spawner (PyroSpawners, RoseStacker),
     * whatever spawn reason it used.
     */
    public static boolean fromForeignSpawner(Set<NamespacedKey> keys) {
        for (NamespacedKey key : keys) {
            if (key.getNamespace().equals("pyrospawners"))
                return true;
            if (key.getNamespace().equals("rosestacker")
                    && (key.getKey().equals("spawner_spawned") || key.getKey().equals("trial_spawner_spawned")))
                return true;
        }
        return false;
    }
}
