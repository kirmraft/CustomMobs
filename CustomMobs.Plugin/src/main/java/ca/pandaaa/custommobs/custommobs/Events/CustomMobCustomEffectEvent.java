package ca.pandaaa.custommobs.custommobs.Events;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

import ca.pandaaa.custommobs.custommobs.CustomEffects.CustomMobCustomEffect;

/**
 * Fired before a CustomMob effect acts. Cancel it to stop the effect.
 *
 * <p>Contract: {@link #getEntity()} is always the CustomMob that owns the effect (for on-hit effects, the mob that hit
 * or shot). {@link #getTarget()} is the player affected, or null for the activation of an area effect (Explosion,
 * GravityPull, LightningStrike, MobSpawner, Trail, Trampoline, Vanish), which then fire one more event per player
 * they reach. Fireball fires twice per player (aim, then launch); Trampoline gives up the whole activation if one
 * player's event is cancelled.
 */
public class CustomMobCustomEffectEvent extends Event implements Cancellable {
    private static final HandlerList handlers = new HandlerList();
    private final Entity entity;
    private final CustomMobCustomEffect customMobEffect;
    private final Player target;
    private boolean cancelled;

    public CustomMobCustomEffectEvent(Entity entity, Player target, CustomMobCustomEffect customMobEffect) {
        this.entity = entity;
        this.customMobEffect = customMobEffect;
        this.target = target;
    }

    public Entity getEntity() {
        return entity;
    }

    public Player getTarget() {
        return target;
    }

    public CustomMobCustomEffect getCustomMobEffect() {
        return customMobEffect;
    }

    @Override
    public HandlerList getHandlers() {
        return handlers;
    }

    public static HandlerList getHandlerList() {
        return handlers;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        cancelled = cancel;
    }
}

