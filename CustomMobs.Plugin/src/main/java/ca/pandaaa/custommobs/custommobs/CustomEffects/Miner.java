package ca.pandaaa.custommobs.custommobs.CustomEffects;

import java.util.*;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

import ca.pandaaa.custommobs.configurations.CustomMobConfiguration;
import ca.pandaaa.custommobs.custommobs.CustomMob;
import ca.pandaaa.custommobs.guis.BasicTypes.IntegerGUI;
import ca.pandaaa.custommobs.guis.EditCustomMobs.CustomEffects.CustomEffectOptionsGUI;
import ca.pandaaa.custommobs.utils.CustomMobsItem;
import ca.pandaaa.custommobs.utils.Utils;
import ca.pandaaa.custommobs.utils.WorldGuard;

/**
 * Allows the mob to see players threw blocks (within a defined radius) and to break blocks to reach
 * the nearby players.
 */
public class Miner extends CustomMobCustomEffect {
    public final static Map<UUID, Miner> activeMiners = new HashMap<>();

    /**
     * Determines the radius (in block(s)) around the CustomMob where it should detect player(s).
     * @minimum 1
     * @maximum 32
     */
    private static final String RADIUS = "custom-effects.miner.radius";
    private int radius;

    public Miner(CustomMobConfiguration mobConfiguration) {
        super(mobConfiguration, CustomEffectType.ALWAYS);
        radius = getCustomEffectOption(RADIUS, Integer.class, 25);
    }

    public List<Player> triggerCustomEffect(Entity entity) {
        if (!(entity instanceof org.bukkit.entity.Mob mob)) return null;

        Player target = getNearestPlayer(mob, radius);
        if (target == null) return null;

        if(triggerCustomEffectCancelled(entity, target, this)) return null;

        // Another plugin refused this target (EntityTargetEvent cancelled): do not dig towards it anyway.
        if (mob.getTarget() != target && !ca.pandaaa.custommobs.custommobs.TargetRequests.request(mob, target))
            return null;

        digTowardsTarget(mob, target);
        return null; // Miner does not have a message.
    }

    public ItemStack modifyOption(Player clicker, CustomMob customMob, String option, ClickType clickType) {
        switch (option.toLowerCase()) {
            case "radius": {
                if (clickType.isRightClick()) {
                    radius = 25;
                    setCustomEffectOption(RADIUS, this.radius);
                } else {
                    new IntegerGUI("Radius", false, 1, 32, (value) -> {
                        this.radius = value;
                        setCustomEffectOption(RADIUS, this.radius);
                        new CustomEffectOptionsGUI(customMob, this, getOptionsItems()).openInventory(clicker);
                    }).openInventory(clicker, radius);
                }
                return getCustomEffectOptionItemStack(getRadiusItem(), true);
            }
            default:
                return handleMessageOption(clicker, customMob, option, clickType);
        }
    }

    public ItemStack getCustomEffectItem() {
        CustomMobsItem item = new CustomMobsItem(Material.DIAMOND_PICKAXE);
        item.setName("&7&lMiner");
        String status = this.enabled ? "&a&lOn" : "&c&lOff";
        item.addLore("&eMiner: &f" + status);
        item.addLore("&bType: &f" + Utils.getSentenceCase(this.customEffectType.name()));
        item.addLore("", "&7&o(( Mines blocks to reach players through walls ))");
        item.setCustomEffectPersistentDataContainer(this.getClass().getSimpleName());
        return getCustomEffectItemStack(item, this.getClass().getSimpleName());
    }

    public List<ItemStack> getOptionsItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(getCustomEffectOptionItemStack(getRadiusItem(), true));
        items.add(getMessageItem().getItem());
        return items;
    }

    private CustomMobsItem getRadiusItem() {
        CustomMobsItem item = new CustomMobsItem(Material.ENDER_PEARL);
        item.setName("&a&lFollow radius");
        item.addLore("&eFollow radius: &f" + radius + " block(s)");
        item.setCustomEffectPersistentDataContainer(this.getClass().getSimpleName() + ".Radius");
        return item;
    }

    /** Same rules as the aggressive goal: registered policy (DENY and weights), within the radius. */
    private Player getNearestPlayer(Mob mob, int radius) {
        java.util.List<Player> candidates = new java.util.ArrayList<>();
        for (Entity nearbyEntity : mob.getNearbyEntities(radius, radius, radius)) {
            if (nearbyEntity instanceof Player player
                    && (player.getGameMode() == GameMode.SURVIVAL || player.getGameMode() == GameMode.ADVENTURE))
                candidates.add(player);
        }
        ca.pandaaa.custommobs.api.MobTargetPolicy policy = ca.pandaaa.custommobs.custommobs.TargetPolicies.current();
        return ca.pandaaa.custommobs.custommobs.TargetSelection.choose(null, candidates,
                player -> player.getLocation().distanceSquared(mob.getLocation()), Double.MAX_VALUE,
                player -> policy.evaluate(mob, player), player -> false, player -> true, false);
    }

    /**
     * First block closing the 2-high corridor (feet and head) on the line from the mob's feet to the target's feet,
     * null when the corridor is open: the mob can then walk to the target, a hole at head height is not enough.
     */
    static Block firstObstruction(Location from, Location to) {
        Vector step = to.toVector().subtract(from.toVector());
        double distance = step.length();
        if (distance == 0) return null;
        step.normalize().multiply(0.25);
        // +0.5: the feet cell of a mob standing on a slab or farmland is the one above, never its own floor.
        Location at = from.clone().add(0, 0.5, 0);
        for (double travelled = 0; travelled < distance; travelled += 0.25, at.add(step)) {
            Block head = at.clone().add(0, 1, 0).getBlock();
            if (!head.isPassable()) return head;
            Block feet = at.getBlock();
            if (!feet.isPassable()) return feet;
        }
        return null;
    }

    /** Blocks farther than this from the mob's feet are not dug: the mob walks up to them first. */
    private static final double DIG_REACH = 3.0;

    /** Replaced in tests: wall-clock time and the mob_griefing/WorldGuard check need a server. */
    static java.util.function.LongSupplier clock = System::currentTimeMillis;
    static java.util.function.Predicate<Block> mayBreak = WorldGuard::canBreakBlock;

    // ponytail: one cooldown per CustomMob type (shared by its mobs), per-entity if several Miners must dig at once
    private Long cooldown = 0L;
    private void digTowardsTarget(org.bukkit.entity.Mob mob, Player target) {
        long now = clock.getAsLong();
        if (now < cooldown) return;

        Block block = firstObstruction(mob.getLocation(), target.getLocation());
        if (block == null || block.getLocation().add(0.5, 0.5, 0.5).distance(mob.getLocation()) > DIG_REACH)
            return;
        cooldown = now + 750L;
        // Unbreakable (bedrock, mob_griefing off, WorldGuard): stop here, never dig around it.
        if (isBlockBreakable(block))
            block.breakNaturally();
    }

    private boolean isBlockBreakable(Block block) {
        if (block == null || block.isEmpty()) return false; // = getType().isAir() (CraftBlock), no registry
        if (!mayBreak.test(block)) return false;

        return switch (block.getType()) {
            case BEDROCK, BARRIER, END_PORTAL_FRAME -> false;
            default -> true;
        };
    }

    public int getRadius() {
        return radius;
    }

    public Long getCooldown() {
        return cooldown;
    }
}