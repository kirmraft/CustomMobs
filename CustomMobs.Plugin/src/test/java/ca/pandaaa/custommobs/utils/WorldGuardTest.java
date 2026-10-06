package ca.pandaaa.custommobs.utils;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.protection.RegionResultSet;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.GlobalProtectedRegion;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;

/**
 * Whether Miner may break a block, judged by WorldGuard's own flag calculator on real regions (no mocks of its
 * semantics). Miner is a mob: a non-member everywhere, never a player.
 */
class WorldGuardTest {
    private final GlobalProtectedRegion global = new GlobalProtectedRegion("__global__");

    private static ProtectedRegion region(String id) {
        return new ProtectedCuboidRegion(id, BlockVector3.at(0, 0, 0), BlockVector3.at(10, 10, 10));
    }

    private boolean mayBreak(ProtectedRegion... regions) {
        return WorldGuard.mobMayBreak(new RegionResultSet(new ArrayList<>(List.of(regions)), global));
    }

    @Test
    void wildernessWithoutFlagsLetsTheMobDig() {
        // Askety's resource world: only __global__, no flags. Asking BUILD with a null subject threw an NPE here.
        assertTrue(mayBreak());
    }

    @Test
    void aRegionWithBlockBreakUnsetStaysProtected() {
        assertFalse(mayBreak(region("town")), "BUILD denies non-members and block-break is unset: protected");
    }

    @Test
    void globalBuildDenyWithBlockBreakUnsetStaysProtected() {
        global.setFlag(Flags.BUILD, StateFlag.State.DENY); // e.g. a spawn world
        assertFalse(mayBreak());
    }

    @Test
    void blockBreakDenyStopsTheMob() {
        global.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
        assertFalse(mayBreak());
        ProtectedRegion r = region("r");
        r.setFlag(Flags.BLOCK_BREAK, StateFlag.State.DENY);
        global.setFlag(Flags.BLOCK_BREAK, null);
        assertFalse(mayBreak(r));
    }

    @Test
    void vanillaMobGriefingOffStopsTheMobBeforeWorldGuardIsAsked() {
        org.bukkit.World world = (org.bukkit.World) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {org.bukkit.World.class}, (self, m, a) -> {
                    throw new UnsupportedOperationException("world." + m.getName());
                });
        java.util.function.Predicate<org.bukkit.World> original = WorldGuard.mobGriefing;
        java.util.List<org.bukkit.World> asked = new ArrayList<>();
        WorldGuard.mobGriefing = w -> {
            asked.add(w);
            return false; // gamerule mob_griefing false
        };
        org.bukkit.block.Block block = (org.bukkit.block.Block) java.lang.reflect.Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[] {org.bukkit.block.Block.class}, (self, m, a) -> switch (m.getName()) {
                    case "getWorld" -> world;
                    default -> throw new UnsupportedOperationException("block." + m.getName());
                });
        try {
            // No server in tests: reaching Bukkit/WorldGuard would throw, so false here means the gamerule decided.
            assertFalse(WorldGuard.canBreakBlock(block));
            assertEquals(List.of(world), asked, "the block's own world is the one asked");
        } finally {
            WorldGuard.mobGriefing = original;
        }
    }

    @Test
    void blockBreakAllowLetsTheMobDigEvenInARegion() {
        ProtectedRegion r = region("arena");
        r.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
        assertTrue(mayBreak(r));
    }

    @Test
    void anExplicitBlockBreakAllowOverridesABuildDenyAsInWorldGuard() {
        ProtectedRegion arena = region("arena");
        arena.setFlag(Flags.BUILD, StateFlag.State.DENY);
        arena.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
        assertTrue(mayBreak(arena), "testBuild turns a BUILD deny into none: the explicit block-break decides");
        arena.setFlag(Flags.BLOCK_BREAK, null);
        assertFalse(mayBreak(arena), "without the explicit allow the BUILD deny region stays protected");
    }
}
