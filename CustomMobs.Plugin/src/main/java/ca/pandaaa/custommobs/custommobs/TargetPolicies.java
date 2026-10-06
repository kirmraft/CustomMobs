package ca.pandaaa.custommobs.custommobs;

import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServiceRegisterEvent;
import org.bukkit.event.server.ServiceUnregisterEvent;

import ca.pandaaa.custommobs.api.MobTargetPolicy;
import ca.pandaaa.custommobs.api.TargetVerdict;

/**
 * The registered {@link MobTargetPolicy}, cached so the goals do not query the ServicesManager every tick.
 */
public final class TargetPolicies implements Listener {
    /** No policy registered: every candidate allowed with the same weight (behaviour before policies existed). */
    public static final MobTargetPolicy ALLOW_ALL = (mob, candidate) -> TargetVerdict.ALLOW;

    private static volatile MobTargetPolicy current = ALLOW_ALL;
    private static volatile boolean registered;

    public static MobTargetPolicy current() {
        return current;
    }

    /** True only when another plugin registered a policy: priority re-evaluation is skipped otherwise. */
    public static boolean isRegistered() {
        return registered;
    }

    public static void refresh() {
        MobTargetPolicy found = Bukkit.getServicesManager().load(MobTargetPolicy.class);
        set(found);
    }

    /** Sets the policy directly (tests; plugins should register through the ServicesManager). Null = none. */
    public static void set(MobTargetPolicy policy) {
        current = policy == null ? ALLOW_ALL : policy;
        registered = policy != null;
    }

    @EventHandler
    public void onRegister(ServiceRegisterEvent event) {
        if (event.getProvider().getService() == MobTargetPolicy.class)
            refresh();
    }

    @EventHandler
    public void onUnregister(ServiceUnregisterEvent event) {
        if (event.getProvider().getService() == MobTargetPolicy.class)
            refresh();
    }
}
