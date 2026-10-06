package ca.pandaaa.custommobs.custommobs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Set;
import java.util.function.Predicate;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.junit.jupiter.api.Test;

class CombatActivationTest {
    private final Player player = Fakes.player("QA_Bot1");
    private final Entity elite = Fakes.entity(Zombie.class, "zombie_elite");
    private final Entity vanilla = Fakes.entity(Zombie.class, "zombie");
    private final Predicate<Entity> customMobs = Set.of(elite)::contains;

    @Test
    void playerHittingACustomMobWakesThatMob() {
        assertSame(elite, CombatActivation.mobHitByPlayer(player, elite, customMobs));
    }

    @Test
    void playerHittingAVanillaMobWakesNothing() {
        assertNull(CombatActivation.mobHitByPlayer(player, vanilla, customMobs));
    }

    @Test
    void customMobHittingAPlayerIsNotAPlayerHit() {
        assertNull(CombatActivation.mobHitByPlayer(elite, player, customMobs));
    }

    @Test
    void mobsHittingEachOtherWakeNothing() {
        assertNull(CombatActivation.mobHitByPlayer(vanilla, elite, customMobs));
    }
}
