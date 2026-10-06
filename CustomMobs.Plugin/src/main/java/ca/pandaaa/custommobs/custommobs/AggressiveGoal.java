package ca.pandaaa.custommobs.custommobs;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Creature;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import com.destroystokyo.paper.entity.ai.MobGoals;

import ca.pandaaa.custommobs.api.MobTargetPolicy;

/**
 * Makes any mob (passive ones included) chase and hit a player.
 * Paper Mob Goals API instead of NMS: Mob.goalSelector is protected on Paper (IllegalAccessError).
 *
 * <p>The target comes from {@link TargetSelection}: follow range from the mob's attribute, the registered
 * {@link MobTargetPolicy}, and vetoes. A veto is another plugin cancelling the {@code EntityTargetEvent} fired by
 * {@code setTarget}: the goal then neither hits nor chases that player, and leaves it alone for a while instead of
 * asking again every tick.
 */
public final class AggressiveGoal implements Goal<Mob> {
    private static final GoalKey<Mob> KEY = GoalKey.of(Mob.class, new NamespacedKey("custommobs", "aggressive"));
    private static final int ATTACK_COOLDOWN_TICKS = 20;
    /** Used when the mob has no follow_range attribute. */
    static final double DEFAULT_FOLLOW_RANGE = 32;
    /** How long a vetoed player is left alone before the goal asks again. */
    static final int VETO_RETRY_TICKS = 20;
    /** With a policy registered, how often a still-valid target is weighed against the others. */
    static final int RECONSIDER_EVERY_TICKS = 20;

    private final Mob mob;
    private final DoubleSupplier followRange;
    private final Supplier<MobTargetPolicy> policy;
    private final Map<UUID, Integer> vetoedUntil = new HashMap<>();
    private int cooldown;
    private int ticks;
    private int nextReconsider;

    AggressiveGoal(Mob mob, DoubleSupplier followRange, Supplier<MobTargetPolicy> policy) {
        this.mob = mob;
        this.followRange = followRange;
        this.policy = policy;
    }

    /** The follow range to use for a mob's follow_range attribute value. */
    static double followRangeOf(Double attributeValue) {
        return attributeValue == null ? DEFAULT_FOLLOW_RANGE : attributeValue;
    }

    /**
     * Idempotent: goals are not saved with the entity, so this is also called again after chunk reloads. The follow
     * range is read from the mob's attribute (set from {@code special.follow-range}) on every search.
     */
    public static void apply(Mob mob) {
        if (mob.getAttribute(Attribute.ATTACK_DAMAGE) == null)
            mob.registerAttribute(Attribute.ATTACK_DAMAGE);
        MobGoals goals = Bukkit.getMobGoals();
        if (!goals.hasGoal(mob, KEY))
            goals.addGoal(mob, 0, new AggressiveGoal(mob, () -> {
                AttributeInstance range = mob.getAttribute(Attribute.FOLLOW_RANGE);
                return followRangeOf(range == null ? null : range.getValue());
            }, TargetPolicies::current));
    }

    @Override
    public boolean shouldActivate() {
        return findTarget() != null;
    }

    @Override
    public void tick() {
        ticks++;
        Player target = findTarget();
        if (target == null)
            return;
        if (mob.getTarget() != target) {
            mob.setTarget(target);
            if (mob.getTarget() != target) {
                // Another plugin refused this target: respect it, do not hit or chase anyway.
                vetoedUntil.put(target.getUniqueId(), ticks + VETO_RETRY_TICKS);
                return;
            }
        }
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
        double range = followRange.getAsDouble();
        MobTargetPolicy p = policy.get();
        LivingEntity current = mob.getTarget();
        Player currentPlayer = current instanceof Player cp && isValidTarget(cp, range) ? cp : null;

        // No policy registered: keep the old behaviour (a valid current target is kept without looking further).
        boolean reconsider = p != TargetPolicies.ALLOW_ALL && ticks >= nextReconsider;
        if (reconsider)
            nextReconsider = ticks + RECONSIDER_EVERY_TICKS;

        List<Player> candidates = new ArrayList<>();
        if (currentPlayer == null || reconsider) {
            for (Player player : mob.getWorld().getPlayers()) {
                if (isValidTarget(player, range))
                    candidates.add(player);
            }
        }
        return TargetSelection.choose(currentPlayer, candidates,
                player -> player.getLocation().distanceSquared(mob.getLocation()), range,
                player -> p.evaluate(mob, player), this::isVetoed,
                player -> mob.getPathfinder().findPath(player) != null, reconsider);
    }

    private boolean isVetoed(Player player) {
        Integer until = vetoedUntil.get(player.getUniqueId());
        if (until == null)
            return false;
        if (ticks < until)
            return true;
        vetoedUntil.remove(player.getUniqueId());
        return false;
    }

    private boolean isValidTarget(Player player, double range) {
        return player.isValid() && !player.isDead()
                && player.getWorld().equals(mob.getWorld())
                && player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR
                && player.getLocation().distanceSquared(mob.getLocation()) <= range * range;
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
