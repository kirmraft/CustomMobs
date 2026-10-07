package ca.pandaaa.custommobs.custommobs.Events;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.junit.jupiter.api.Test;

import ca.pandaaa.custommobs.configurations.CustomMobConfiguration;
import ca.pandaaa.custommobs.custommobs.CustomMob;
import ca.pandaaa.custommobs.custommobs.CustomEffects.CustomEffectType;
import ca.pandaaa.custommobs.custommobs.CustomEffects.CustomMobCustomEffect;

/**
 * On-hit effects (Infection, FireAspect...) run only for a hit that really happened: a hit another plugin cancelled
 * (protection, god mode, combat rules) gives no effect, in melee and with projectiles alike.
 */
class EventsOnHitTest {
    private final List<Entity> sources = new ArrayList<>();
    private final Zombie zombie = fake(Zombie.class, null);
    private final Skeleton skeleton = fake(Skeleton.class, null);
    private final Player player = fake(Player.class, null);

    /** An enabled ON_DAMAGE_ON_PLAYER effect that records who triggered it. */
    class Recorder extends CustomMobCustomEffect {
        Recorder(boolean enabled) {
            super(config(enabled), CustomEffectType.ON_DAMAGE_ON_PLAYER);
        }

        @Override public List<Player> triggerOnDamage(Entity source, Player victim) {
            assertSame(player, victim);
            sources.add(source);
            return null;
        }

        @Override public List<Player> triggerCustomEffect(Entity entity) { return null; }
        @Override public ItemStack modifyOption(Player p, CustomMob c, String o, ClickType t) { return null; }
        @Override public List<ItemStack> getOptionsItems() { return null; }
        @Override public ItemStack getCustomEffectItem() { return null; }
    }

    private static CustomMobConfiguration config(boolean enabled) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("custom-effects.recorder.enabled", enabled);
        return new CustomMobConfiguration(yaml, new File("test.yml"));
    }

    private void hit(boolean cancelled, Entity damager, Entity victim) {
        hit(cancelled, damager, victim, true);
    }

    private void hit(boolean cancelled, Entity damager, Entity victim, boolean enabled) {
        Collection<CustomMobCustomEffect> effects = List.of(new Recorder(enabled));
        Events.triggerOnHitEffects(cancelled, damager, victim, e -> e == zombie || e == skeleton ? effects : null);
    }

    @Test
    void meleeHitTriggersTheMobsEffects() {
        hit(false, zombie, player);
        assertEquals(List.of(zombie), sources);
    }

    @Test
    void cancelledMeleeHitTriggersNothing() {
        hit(true, zombie, player);
        assertTrue(sources.isEmpty());
    }

    @Test
    void projectileHitTriggersTheShootersEffects() {
        hit(false, fake(Arrow.class, skeleton), player);
        assertEquals(List.of(skeleton), sources);
    }

    @Test
    void cancelledProjectileHitTriggersNothing() {
        hit(true, fake(Arrow.class, skeleton), player);
        assertTrue(sources.isEmpty());
    }

    @Test
    void disabledEffectIsNotTriggered() {
        hit(false, zombie, player, false);
        assertTrue(sources.isEmpty());
    }

    @Test
    void hitOnANonPlayerTriggersNothing() {
        hit(false, zombie, skeleton);
        assertTrue(sources.isEmpty());
    }

    /**
     * Another plugin may cancel the hit after us (HIGH/HIGHEST): the effects listen at MONITOR, where the outcome is
     * final, and Bukkit skips them for a cancelled hit.
     */
    @Test
    void effectsListenToTheFinalOutcomeOfTheHit() throws NoSuchMethodException {
        Method handler = Events.class.getMethod("onHitEffects", EntityDamageByEntityEvent.class);
        EventHandler annotation = handler.getAnnotation(EventHandler.class);
        assertNotNull(annotation);
        assertEquals(EventPriority.MONITOR, annotation.priority());
        assertTrue(annotation.ignoreCancelled());
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> T fake(Class<T> type, Object shooter) {
        return (T) Proxy.newProxyInstance(EventsOnHitTest.class.getClassLoader(), new Class<?>[] {type},
                (self, m, a) -> switch (m.getName()) {
                    case "getShooter" -> shooter;
                    case "equals" -> self == a[0];
                    case "hashCode" -> System.identityHashCode(self);
                    case "toString" -> type.getSimpleName();
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }
}
