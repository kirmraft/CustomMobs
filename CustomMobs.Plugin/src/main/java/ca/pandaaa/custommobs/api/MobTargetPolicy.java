package ca.pandaaa.custommobs.api;

import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

/**
 * Lets another plugin decide which players a CustomMob may target and which it prefers.
 *
 * <p>Register one through Bukkit's {@code ServicesManager}. CustomMobs asks it only while it is already looking for a
 * target (aggressive goal, Miner), for candidates within the mob's follow range. Keep it cheap: no database or
 * placeholder lookups here, cache them on your side. With no policy registered CustomMobs behaves as before.
 *
 * <p>A policy can only narrow or reorder the candidates: it never makes a mob attack a player that another plugin
 * refused through {@code EntityTargetEvent}.
 */
@FunctionalInterface
public interface MobTargetPolicy {
    TargetVerdict evaluate(Mob mob, Player candidate);
}
