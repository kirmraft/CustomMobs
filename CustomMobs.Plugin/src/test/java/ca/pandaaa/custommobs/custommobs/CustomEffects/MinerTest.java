package ca.pandaaa.custommobs.custommobs.CustomEffects;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ca.pandaaa.custommobs.api.TargetVerdict;
import ca.pandaaa.custommobs.configurations.CustomMobConfiguration;
import ca.pandaaa.custommobs.custommobs.TargetPolicies;

/** Miner picks its own target: it must respect the policy and other plugins' vetoes like the aggressive goal. */
class MinerTest {
    private final java.util.function.Consumer<Event> original = CustomMobCustomEffect.dispatcher;
    private final World world = fake(World.class, "world");
    private final List<Player> nearby = new ArrayList<>();
    private final Set<Player> vetoed = new HashSet<>();
    private LivingEntity target;
    private boolean dug;

    private final Zombie mob = (Zombie) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Zombie.class},
            (self, m, a) -> switch (m.getName()) {
                case "getNearbyEntities" -> new ArrayList<Entity>(nearby);
                case "getLocation" -> new Location(world, 0, 64, 0);
                case "getTarget" -> target;
                case "setTarget" -> {
                    if (!vetoed.contains(a[0]))
                        target = (LivingEntity) a[0];
                    yield null;
                }
                case "getEyeLocation" -> {
                    dug = true; // first thing the digging path asks for
                    throw new UnsupportedOperationException("digging reached");
                }
                case "equals" -> self == a[0];
                case "hashCode" -> System.identityHashCode(self);
                default -> throw new UnsupportedOperationException("mob." + m.getName());
            });

    MinerTest() {
        CustomMobCustomEffect.dispatcher = e -> { };
    }

    @AfterEach
    void restore() {
        CustomMobCustomEffect.dispatcher = original;
        TargetPolicies.set(null);
    }

    private Player player(String name, double x) {
        Player p = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
                (self, m, a) -> switch (m.getName()) {
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "getLocation" -> new Location(world, x, 64, 0);
                    case "equals" -> self == a[0];
                    case "hashCode" -> System.identityHashCode(self);
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(name + "." + m.getName());
                });
        nearby.add(p);
        return p;
    }

    private Miner miner() {
        return new Miner(new CustomMobConfiguration(new YamlConfiguration(), new File("t.yml")));
    }

    @Test
    void vetoedTargetIsNotDugTowards() {
        vetoed.add(player("non_morto", 3));
        assertNull(miner().triggerCustomEffect(mob));
        assertFalse(dug);
        assertNull(target);
    }

    @Test
    void deniedPlayerIsNotChosen() {
        Player denied = player("denied", 3);
        TargetPolicies.set((m, p) -> p == denied ? TargetVerdict.DENY : TargetVerdict.ALLOW);
        assertNull(miner().triggerCustomEffect(mob));
        assertNull(target);
    }

    @Test
    void allowedPlayerIsTargetedAndDugTowards() {
        Player p = player("p", 3);
        assertThrows(UnsupportedOperationException.class, () -> miner().triggerCustomEffect(mob));
        assertSame(p, target);
        assertTrue(dug);
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, String name) {
        return (T) Proxy.newProxyInstance(MinerTest.class.getClassLoader(), new Class<?>[] {type},
                (self, m, a) -> switch (m.getName()) {
                    case "equals" -> self == a[0];
                    case "hashCode" -> System.identityHashCode(self);
                    default -> throw new UnsupportedOperationException(name + "." + m.getName());
                });
    }
}
