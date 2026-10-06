package ca.pandaaa.custommobs.custommobs;

import java.util.function.Consumer;

import org.bukkit.Bukkit;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * Asks other plugins before a CustomMob targets a player. On Paper, {@code Mob#setTarget} fires no
 * {@code EntityTargetEvent} (CraftMob passes a null reason), so without this nobody could refuse the target.
 */
public final class TargetRequests {
    /** Fires the event; replaced in tests (no server there). */
    public static Consumer<Event> dispatcher = event -> Bukkit.getPluginManager().callEvent(event);

    private TargetRequests() {
    }

    /** True if the mob now targets the player; false if another plugin cancelled (or redirected) the target. */
    public static boolean request(Mob mob, Player player) {
        EntityTargetLivingEntityEvent event = new EntityTargetLivingEntityEvent(mob, player, EntityTargetEvent.TargetReason.CUSTOM);
        dispatcher.accept(event);
        // ponytail: a redirect counts as a refusal; follow event.getTarget() if a plugin ever needs redirects
        if (event.isCancelled() || event.getTarget() != player)
            return false;
        mob.setTarget(player);
        return true;
    }
}
