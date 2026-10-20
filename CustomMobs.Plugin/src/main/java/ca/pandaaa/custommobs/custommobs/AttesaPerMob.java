package ca.pandaaa.custommobs.custommobs;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-mob wait between two uses of an effect (Charge, Infection, MobSpawner). An effect object is shared by every mob
 * of its CustomMob type, so the wait is kept per entity. Expired waits are dropped when a new one starts, so dead mobs
 * do not stay in the map.
 */
public final class AttesaPerMob {
    /** Replaced in tests: wall-clock time. */
    public static java.util.function.LongSupplier clock = System::currentTimeMillis;
    private final Map<UUID, Long> fine = new HashMap<>();

    public boolean pronto(UUID mob) {
        Long t = fine.get(mob);
        return t == null || clock.getAsLong() >= t;
    }

    public void avvia(UUID mob, int secondi) {
        if (secondi <= 0) return;
        long ora = clock.getAsLong();
        fine.values().removeIf(t -> t <= ora);
        fine.put(mob, ora + secondi * 1000L);
    }
}
