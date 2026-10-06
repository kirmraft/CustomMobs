package ca.pandaaa.custommobs.custommobs;

import java.util.List;

import org.bukkit.entity.Player;

/**
 * When a CustomMob's next COOLDOWN effect may fire. Today (M15.2, unchanged behaviour): one effect is picked at random
 * among the enabled COOLDOWN effects, and whatever it returns, including null for "cancelled" or "nobody reached",
 * the whole cooldown starts again: a cancelled activation is lost, not retried.
 */
public final class AbilityCycle {
    private AbilityCycle() {
    }

    /** Seconds before the next pick, given what the picked effect returned. */
    public static int secondsUntilNext(List<Player> result, int cooldownSeconds) {
        return cooldownSeconds;
    }
}
