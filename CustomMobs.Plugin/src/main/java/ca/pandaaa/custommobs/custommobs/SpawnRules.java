package ca.pandaaa.custommobs.custommobs;

import java.util.EnumSet;
import java.util.Set;

import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;

/**
 * Which vanilla spawns the natural replacement may turn into a CustomMob.
 * Whitelist: only real world spawns. Spawners (any plugin), eggs, commands, breeding, splits, conversions,
 * reinforcements and every other reason (including future ones) are excluded.
 */
public final class SpawnRules {
    private static final Set<SpawnReason> REPLACEABLE = EnumSet.of(SpawnReason.NATURAL, SpawnReason.CHUNK_GEN);

    private SpawnRules() {
    }

    public static boolean replaceable(SpawnReason reason) {
        return REPLACEABLE.contains(reason);
    }
}
