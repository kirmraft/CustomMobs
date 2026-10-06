package ca.pandaaa.custommobs.custommobs.CustomEffects;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ca.pandaaa.custommobs.configurations.CustomMobConfiguration;
import ca.pandaaa.custommobs.custommobs.Events.CustomMobCustomEffectEvent;

/** Contract of CustomMobCustomEffectEvent for on-hit effects: entity = the CustomMob, target = the player it hit. */
class EffectEventContractTest {
    private final List<CustomMobCustomEffectEvent> fired = new ArrayList<>();
    private final java.util.function.Consumer<Event> original = CustomMobCustomEffect.dispatcher;
    private final List<Integer> fireTicks = new ArrayList<>();
    private boolean cancelAll;

    private final Zombie mob = fake(Zombie.class, "mob");
    private final Player victim = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
            (self, m, a) -> switch (m.getName()) {
                case "setFireTicks" -> {
                    fireTicks.add((Integer) a[0]);
                    yield null;
                }
                case "equals" -> self == a[0];
                case "hashCode" -> System.identityHashCode(self);
                case "toString" -> "victim";
                default -> throw new UnsupportedOperationException("victim." + m.getName());
            });

    EffectEventContractTest() {
        CustomMobCustomEffect.dispatcher = e -> {
            CustomMobCustomEffectEvent event = (CustomMobCustomEffectEvent) e;
            event.setCancelled(cancelAll);
            fired.add(event);
        };
    }

    @AfterEach
    void restore() {
        CustomMobCustomEffect.dispatcher = original;
    }

    private static CustomMobConfiguration config() {
        return new CustomMobConfiguration(new YamlConfiguration(), new File("test.yml"));
    }

    @Test
    void onHitEventNamesTheMobAsSourceAndTheVictimAsTarget() {
        new FireAspect(config()).triggerOnDamage(mob, victim);
        assertEquals(1, fired.size());
        assertSame(mob, fired.get(0).getEntity(), "source must be the CustomMob, not the victim");
        assertSame(victim, fired.get(0).getTarget());
        assertEquals(List.of(100), fireTicks, "the victim burns (5 s default)");
    }

    @Test
    void cancelledOnHitEffectDoesNothing() {
        cancelAll = true;
        assertNull(new FireAspect(config()).triggerOnDamage(mob, victim));
        assertTrue(fireTicks.isEmpty());
    }

    @Test
    void anyOnHitEffectFiresOneEventWithSourceAndTarget() {
        CustomMobCustomEffect generic = new CustomMobCustomEffect(config(), CustomEffectType.ON_DAMAGE_ON_PLAYER) {
            @Override public List<Player> triggerCustomEffect(Entity entity) { return new ArrayList<>(); }
            @Override public org.bukkit.inventory.ItemStack modifyOption(Player p, ca.pandaaa.custommobs.custommobs.CustomMob c, String o, org.bukkit.event.inventory.ClickType t) { return null; }
            @Override public List<org.bukkit.inventory.ItemStack> getOptionsItems() { return null; }
            @Override public org.bukkit.inventory.ItemStack getCustomEffectItem() { return null; }
        };
        generic.triggerOnDamage(mob, victim);
        assertEquals(1, fired.size());
        assertSame(mob, fired.get(0).getEntity());
        assertSame(victim, fired.get(0).getTarget());
    }

    @SuppressWarnings("unchecked")
    private static <T> T fake(Class<T> type, String name) {
        return (T) Proxy.newProxyInstance(EffectEventContractTest.class.getClassLoader(), new Class<?>[] {type},
                (self, m, a) -> switch (m.getName()) {
                    case "equals" -> self == a[0];
                    case "hashCode" -> System.identityHashCode(self);
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(name + "." + m.getName());
                });
    }
}
