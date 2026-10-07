package ca.pandaaa.custommobs.custommobs.Events;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;

import org.bukkit.block.Block;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Skeleton;
import org.bukkit.entity.Zombie;
import org.bukkit.projectiles.BlockProjectileSource;
import org.junit.jupiter.api.Test;

/** On-hit effects (Infection...) run for the CustomMob that hit: itself in melee, the shooter of a projectile. */
class EventsAttackerTest {
    @Test
    void meleeAttackerIsTheMobItself() {
        Zombie zombie = fake(Zombie.class, null);
        assertSame(zombie, Events.attacker(zombie));
    }

    @Test
    void projectileAttackerIsItsLivingShooter() {
        Skeleton skeleton = fake(Skeleton.class, null);
        assertSame(skeleton, Events.attacker(fake(Arrow.class, skeleton)));
    }

    @Test
    void projectileFromADispenserStaysTheProjectile() {
        BlockProjectileSource dispenser = (BlockProjectileSource) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {BlockProjectileSource.class}, (self, m, a) -> {
                    throw new UnsupportedOperationException(m.getName());
                });
        Arrow arrow = fake(Arrow.class, dispenser);
        assertSame(arrow, Events.attacker(arrow));
    }

    @SuppressWarnings("unchecked")
    private static <T extends Entity> T fake(Class<T> type, Object shooter) {
        return (T) Proxy.newProxyInstance(EventsAttackerTest.class.getClassLoader(), new Class<?>[] {type},
                (self, m, a) -> switch (m.getName()) {
                    case "getShooter" -> shooter;
                    case "equals" -> self == a[0];
                    case "hashCode" -> System.identityHashCode(self);
                    default -> throw new UnsupportedOperationException(m.getName());
                });
    }
}
