package ca.pandaaa.custommobs.custommobs;

import java.util.function.Predicate;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/**
 * Which CustomMob a hit should wake up (boss bar, custom effects, damage tracking).
 */
public final class CombatActivation {
    private CombatActivation() {
    }

    /**
     * @return the CustomMob a player just hit, or null when the hit is anything else.
     */
    public static Entity mobHitByPlayer(Entity damager, Entity victim, Predicate<Entity> isCustomMob) {
        if (!(damager instanceof Player))
            return null;
        // Upstream checked the attacker here (always a Player), so a hit never woke anything up.
        return isCustomMob.test(victim) ? victim : null;
    }
}
