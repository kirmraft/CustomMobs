package ca.pandaaa.custommobs.custommobs.CustomEffects;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Spider;
import org.bukkit.event.Event;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ca.pandaaa.custommobs.configurations.CustomMobConfiguration;

/**
 * Charge runs every 0.5 s while a player is next to the mob: with a cooldown the player is launched once per wait,
 * not on every tick, and each mob waits on its own.
 */
class ChargeTest {
    private final java.util.function.Consumer<Event> original = CustomMobCustomEffect.dispatcher;
    private final java.util.function.LongSupplier originalClock = Charge.clock;
    private long now = 1_000_000;
    private int launches;
    private final Player player = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
            (self, m, a) -> switch (m.getName()) {
                case "getLocation" -> new Location(null, 0, 64, 0);
                case "setVelocity" -> {
                    launches++;
                    yield null;
                }
                case "equals" -> self == a[0];
                case "hashCode" -> System.identityHashCode(self);
                default -> throw new UnsupportedOperationException("player." + m.getName());
            });

    ChargeTest() {
        CustomMobCustomEffect.dispatcher = e -> { };
        Charge.clock = () -> now;
    }

    @AfterEach
    void restore() {
        CustomMobCustomEffect.dispatcher = original;
        Charge.clock = originalClock;
    }

    private Spider mob() {
        UUID id = UUID.randomUUID();
        return (Spider) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Spider.class},
                (self, m, a) -> switch (m.getName()) {
                    case "getNearbyEntities" -> new ArrayList<Entity>(List.of(player));
                    case "getUniqueId" -> id;
                    case "equals" -> self == a[0];
                    case "hashCode" -> System.identityHashCode(self);
                    default -> throw new UnsupportedOperationException("mob." + m.getName());
                });
    }

    private Charge charge(Integer cooldown) {
        YamlConfiguration yaml = new YamlConfiguration();
        if (cooldown != null)
            yaml.set("custom-effects.charge.cooldown", cooldown);
        return new Charge(new CustomMobConfiguration(yaml, new File("t.yml")));
    }

    @Test
    void withoutACooldownEveryTickLaunchesAgain() {
        Charge charge = charge(null);
        Spider mob = mob();
        for (int i = 0; i < 4; i++) {
            charge.triggerCustomEffect(mob);
            now += 500;
        }
        assertEquals(4, launches);
    }

    @Test
    void aCooldownLaunchesOncePerWait() {
        Charge charge = charge(10);
        Spider mob = mob();
        for (int i = 0; i < 20; i++) { // 10 s of ticks
            charge.triggerCustomEffect(mob);
            now += 500;
        }
        assertEquals(1, launches);
        charge.triggerCustomEffect(mob); // 10 s later
        assertEquals(2, launches);
    }

    @Test
    void eachMobWaitsOnItsOwn() {
        Charge charge = charge(10);
        charge.triggerCustomEffect(mob());
        charge.triggerCustomEffect(mob());
        assertEquals(2, launches);
    }
}
