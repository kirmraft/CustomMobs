package ca.pandaaa.custommobs.custommobs;

import java.util.EnumSet;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Creature;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;

/**
 * Makes any mob (passive ones included) chase and hit the nearest player.
 * Paper Mob Goals API instead of NMS: Mob.goalSelector is protected on Paper (IllegalAccessError).
 */
public final class AggressiveGoal implements Goal<Mob> {
    private static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("custommobs", "aggressive"));
    private static final int ATTACK_COOLDOWN_TICKS = 20;

    private final Mob mob;
    private final double followRange;
    private int cooldown;

    private AggressiveGoal(Mob mob, double followRange) {
        this.mob = mob;
        this.followRange = followRange;
    }

    /** Idempotent: goals are not saved with the entity, so this is also called again after chunk reloads. */
    public static void apply(Mob mob, double followRange) {
        if (mob.getAttribute(Attribute.ATTACK_DAMAGE) == null)
            mob.registerAttribute(Attribute.ATTACK_DAMAGE);
        MobGoals goals = Bukkit.getMobGoals();
        if (!goals.hasGoal(mob, KEY))
            goals.addGoal(mob, 0, new AggressiveGoal(mob, followRange));
    }

    @Override
    public boolean shouldActivate() {
        return findTarget() != null;
    }

    @Override
    public void tick() {
        Player target = findTarget();
        if (target == null)
            return;
        mob.setTarget(target);
        mob.lookAt(target);
        if (cooldown > 0)
            cooldown--;

        double reach = mob.getWidth() + 1.5;
        if (mob.getLocation().distanceSquared(target.getLocation()) <= reach * reach) {
            if (cooldown == 0) {
                mob.attack(target);
                mob.swingMainHand();
                cooldown = ATTACK_COOLDOWN_TICKS;
            }
        } else if (mob instanceof Creature) {
            mob.getPathfinder().moveTo(target, 1.3);
        } else {
            // Flying/swimming mobs without ground pathfinding: push towards the target.
            mob.setVelocity(target.getLocation().toVector().subtract(mob.getLocation().toVector()).normalize().multiply(0.3));
        }
    }

    private Player findTarget() {
        if (mob.getTarget() instanceof Player current && isValidTarget(current))
            return current;
        Player nearest = null;
        double best = followRange * followRange;
        for (Player player : mob.getWorld().getPlayers()) {
            if (!isValidTarget(player))
                continue;
            double distance = player.getLocation().distanceSquared(mob.getLocation());
            if (distance <= best) {
                best = distance;
                nearest = player;
            }
        }
        return nearest;
    }

    private boolean isValidTarget(Player player) {
        return player.isValid() && !player.isDead()
                && player.getWorld().equals(mob.getWorld())
                && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                && player.getLocation().distanceSquared(mob.getLocation()) <= followRange * followRange;
    }

    @Override
    public GoalKey<Mob> getKey() {
        return KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.LOOK, GoalType.TARGET);
    }
}
