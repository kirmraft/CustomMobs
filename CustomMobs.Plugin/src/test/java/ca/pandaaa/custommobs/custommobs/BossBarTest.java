package ca.pandaaa.custommobs.custommobs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

class BossBarTest {
    private final UUID mob = UUID.randomUUID();
    private final List<Player> viewers = new ArrayList<>();
    private final BossBar bars = new BossBar();

    BossBarTest() {
        bars.createBossBar(mob, Fakes.recordingBar(viewers));
    }

    @Test
    void everyNearbyPlayerSeesTheBarOnce() {
        Player a = Fakes.player("a");
        Player b = Fakes.player("b");
        bars.addPlayerToBossBar(a, mob);
        bars.addPlayerToBossBar(b, mob);
        bars.addPlayerToBossBar(a, mob);
        assertEquals(List.of(a, b), viewers);
    }

    @Test
    void deathDeletesTheBarAndItsViewers() {
        bars.addPlayerToBossBar(Fakes.player("a"), mob);
        bars.deleteBossBar(mob);
        assertTrue(viewers.isEmpty());
        assertNull(bars.getBossBar(mob));
    }

    @Test
    void aMobWithoutBarIsIgnored() {
        bars.addPlayerToBossBar(Fakes.player("a"), UUID.randomUUID());
        assertTrue(viewers.isEmpty());
    }
}
