package ca.pandaaa.custommobs.custommobs;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

import com.destroystokyo.paper.entity.Pathfinder;

/** A tiny world for goal tests: one mob, some players, a target veto like Origini's. No server. */
final class Scene {
    final World world = proxy(World.class, "world", Map.of("getPlayers", a -> new ArrayList<>(this.players)));
    final List<Player> players = new ArrayList<>();
    /** Players another plugin refuses as target: their EntityTargetEvent is cancelled. */
    final Set<Player> vetoed = new HashSet<>();
    final Set<Player> unreachable = new HashSet<>();
    final List<Player> attacked = new ArrayList<>();
    final List<Entity> chased = new ArrayList<>();
    final List<LivingEntity> setTargetCalls = new ArrayList<>();
    /** Every EntityTargetEvent fired at other plugins (the vetoed ones are cancelled, like Origini does). */
    final List<EntityTargetLivingEntityEvent> targetEvents = new ArrayList<>();
    LivingEntity target;
    /** The mob's age; tests advance it like the server does every tick, goal active or not. */
    int ticksLived;
    final Zombie mob;

    Scene() {
        TargetRequests.dispatcher = e -> {
            EntityTargetLivingEntityEvent event = (EntityTargetLivingEntityEvent) e;
            targetEvents.add(event);
            if (vetoed.contains(event.getTarget()))
                event.setCancelled(true);
        };
        Pathfinder pathfinder = proxy(Pathfinder.class, "pathfinder", Map.of(
                "moveTo", a -> {
                    chased.add((Entity) a[0]);
                    return true;
                },
                "findPath", a -> unreachable.contains(a[0]) ? null : proxy(Pathfinder.PathResult.class, "path", Map.of())));
        mob = proxy(Zombie.class, "mob", Map.ofEntries(
                Map.entry("getLocation", a -> new Location(world, 0, 64, 0)),
                Map.entry("getWorld", a -> world),
                Map.entry("getWidth", a -> 0.6),
                Map.entry("getTarget", a -> target),
                Map.entry("getTicksLived", a -> ticksLived),
                Map.entry("setTarget", a -> {
                    LivingEntity wanted = (LivingEntity) a[0];
                    setTargetCalls.add(wanted);
                    target = wanted; // Paper: Mob#setTarget fires no EntityTargetEvent, nobody can refuse it here
                    return null;
                }),
                Map.entry("attack", a -> {
                    attacked.add((Player) a[0]);
                    return null;
                }),
                Map.entry("lookAt", a -> null),
                Map.entry("swingMainHand", a -> null),
                Map.entry("getPathfinder", a -> pathfinder),
                Map.entry("isValid", a -> true)));
    }

    Player player(String name, double x) {
        UUID id = UUID.randomUUID();
        Player p = proxy(Player.class, name, Map.of(
                "getLocation", a -> new Location(world, x, 64, 0),
                "getWorld", a -> world,
                "isValid", a -> true,
                "isDead", a -> false,
                "getGameMode", a -> GameMode.SURVIVAL,
                "getUniqueId", a -> id,
                "getName", a -> name));
        players.add(p);
        return p;
    }

    @SuppressWarnings("unchecked")
    static <T> T proxy(Class<T> type, String name, Map<String, Function<Object[], Object>> methods) {
        return (T) Proxy.newProxyInstance(Scene.class.getClassLoader(), new Class<?>[] {type}, (self, method, args) -> {
            switch (method.getName()) {
                case "equals": return self == args[0];
                case "hashCode": return System.identityHashCode(self);
                case "toString": return name;
                default:
            }
            Function<Object[], Object> f = methods.get(method.getName());
            if (f == null)
                throw new UnsupportedOperationException(name + "." + method.getName());
            return f.apply(args == null ? new Object[0] : args);
        });
    }
}
