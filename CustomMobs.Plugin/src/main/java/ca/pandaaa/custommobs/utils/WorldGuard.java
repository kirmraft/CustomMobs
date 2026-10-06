package ca.pandaaa.custommobs.utils;

import java.util.function.Predicate;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.domains.Association;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.association.Associables;
import com.sk89q.worldguard.protection.association.RegionAssociable;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.World;
import org.bukkit.block.Block;

public class WorldGuard {
    /** A mob (Miner) is a non-member of every region: never a player, never anyone's stand-in. */
    private static final RegionAssociable MOB = Associables.constant(Association.NON_MEMBER);

    /** The world's vanilla mob_griefing gamerule; replaced in tests (GameRules needs a server). */
    static Predicate<World> mobGriefing = world -> Boolean.TRUE.equals(world.getGameRuleValue(GameRules.MOB_GRIEFING));

    /** Whether a mob (Miner) may break this block: vanilla mob_griefing first, then WorldGuard. */
    public static boolean canBreakBlock(Block block) {
        if (!mobGriefing.test(block.getWorld()))
            return false;
        if (Bukkit.getPluginManager().getPlugin("WorldGuard") == null)
            return true;

        RegionContainer container = com.sk89q.worldguard.WorldGuard.getInstance().getPlatform().getRegionContainer();
        return mobMayBreak(container.createQuery().getApplicableRegions(BukkitAdapter.adapt(block.getLocation())));
    }

    /**
     * The rule WorldGuard itself applies when a non-player breaks a block ({@code RegionQuery.testBuild} with
     * {@code block-break}, checked on 7.0.18): block-break DENY stops it, ALLOW lets it; unset falls back to BUILD,
     * where a region refuses non-members (protected) and the bare wilderness allows. BUILD needs a subject: asking it
     * with null threw an NPE on every dig.
     */
    static boolean mobMayBreak(ApplicableRegionSet regions) {
        return StateFlag.test(StateFlag.denyToNone(regions.queryState(MOB, Flags.BUILD)),
                regions.queryState(MOB, Flags.BLOCK_BREAK));
    }
}
