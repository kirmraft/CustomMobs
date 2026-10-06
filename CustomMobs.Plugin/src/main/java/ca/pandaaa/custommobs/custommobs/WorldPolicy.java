package ca.pandaaa.custommobs.custommobs;

import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Which worlds a CustomMob may exist in, from the optional {@code worlds:} list of its file.
 * Missing list = every world (legacy behaviour); empty list = no world.
 */
public final class WorldPolicy {
    private final Set<String> worlds; // null = every world

    private WorldPolicy(Set<String> worlds) {
        this.worlds = worlds;
    }

    public static WorldPolicy of(List<String> worlds) {
        if (worlds == null)
            return new WorldPolicy(null);
        return new WorldPolicy(worlds.stream().map(world -> world.toLowerCase(Locale.ROOT)).collect(Collectors.toUnmodifiableSet()));
    }

    public boolean allows(String worldName) {
        if (worlds == null)
            return true;
        return worldName != null && worlds.contains(worldName.toLowerCase(Locale.ROOT));
    }

    public boolean isRestricted() {
        return worlds != null;
    }

    public String describe() {
        if (worlds == null)
            return "all worlds";
        if (worlds.isEmpty())
            return "no world";
        return worlds.stream().sorted().collect(Collectors.toList()).toString();
    }
}
