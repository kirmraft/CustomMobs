package ca.pandaaa.custommobs.custommobs;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

/** Bukkit interfaces faked with Proxy: the fork has no MockBukkit. */
final class Fakes {
    private Fakes() {
    }

    @SuppressWarnings("unchecked")
    static <T> T of(Class<T> type, String name) {
        return (T) Proxy.newProxyInstance(Fakes.class.getClassLoader(), new Class<?>[] {type}, (proxy, method, args) ->
                switch (method.getName()) {
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(name + "." + method.getName());
                });
    }

    static Player player(String name) {
        return of(Player.class, name);
    }

    static Entity entity(Class<? extends Entity> type, String name) {
        return of(type, name);
    }

    /** A boss bar that only remembers who sees it. */
    static BossBar recordingBar(List<Player> viewers) {
        return (BossBar) Proxy.newProxyInstance(Fakes.class.getClassLoader(), new Class<?>[] {BossBar.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "addPlayer" -> {
                        if (!viewers.contains((Player) args[0]))
                            viewers.add((Player) args[0]);
                        yield null;
                    }
                    case "removeAll" -> {
                        viewers.clear();
                        yield null;
                    }
                    case "getPlayers" -> new ArrayList<>(viewers);
                    case "equals" -> proxy == args[0];
                    case "hashCode" -> System.identityHashCode(proxy);
                    case "toString" -> "bar";
                    default -> throw new UnsupportedOperationException("BossBar." + method.getName());
                });
    }
}
