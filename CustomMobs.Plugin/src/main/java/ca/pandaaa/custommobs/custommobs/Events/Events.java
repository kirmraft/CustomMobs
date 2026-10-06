package ca.pandaaa.custommobs.custommobs.Events;

import java.util.*;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.*;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.RayTraceResult;

import ca.pandaaa.custommobs.CustomMobs;
import ca.pandaaa.custommobs.custommobs.*;
import ca.pandaaa.custommobs.custommobs.CustomEffects.*;
import ca.pandaaa.custommobs.custommobs.Messages.Message;
import ca.pandaaa.custommobs.custommobs.Messages.SpawnDeathMessage;
import ca.pandaaa.custommobs.custommobs.Options.Special;

public class Events implements Listener {
    private static final Random RANDOM = new Random();

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getDamager();

        if (entity instanceof Projectile projectile) {
            if (projectile.getShooter() instanceof LivingEntity)
                entity = (LivingEntity) projectile.getShooter();
        }

        if (entity == null)
            return;

        Set<NamespacedKey> keys = entity.getPersistentDataContainer().getKeys();
        if (keys.contains(NamespacedKeys.KEY_MIN_DAMAGE) && keys.contains(NamespacedKeys.KEY_MAX_DAMAGE)) {
            double minDamage = entity.getPersistentDataContainer().get(NamespacedKeys.KEY_MIN_DAMAGE,
                    PersistentDataType.DOUBLE);
            double maxDamage = entity.getPersistentDataContainer().get(NamespacedKeys.KEY_MAX_DAMAGE,
                    PersistentDataType.DOUBLE);
            double randomValue = minDamage + (maxDamage - minDamage) * RANDOM.nextDouble();
            event.setDamage(randomValue);
        }

        if (!(event.getEntity() instanceof Player))
            return;

        if (isCustomMob(entity)) {
            Entity source = entity;
            CustomMob customMob = CustomMobs.getPlugin().getCustomMobsManager().getCustomMob(
                    entity.getPersistentDataContainer().get(NamespacedKeys.KEY_NAME, PersistentDataType.STRING));
            customMob.getCustomMobCustomEffects().stream()
                    .filter(effect -> CustomEffectType.ON_DAMAGE_ON_PLAYER.equals(effect.getCustomEffectType()))
                    .filter(CustomMobCustomEffect::isEnabled)
                    .forEach(effect -> {
                        // Source = the CustomMob that hit (or shot), target = the player hit.
                        if (effect.triggerOnDamage(source, (Player) event.getEntity()) != null)
                            effect.trySendCustomEffectMessage((Player) event.getEntity());
                    });
        }
    }

    /**
     * Damage of players on the CustomMob, which is used for MOST_DAMAGE.
     */
    // ignoreCancelled: a hit a protection plugin cancelled must not count toward MOST_DAMAGE.
    @EventHandler(ignoreCancelled = true)
    public void onPlayerDamage(EntityDamageByEntityEvent event) {
        Entity entity = event.getDamager();

        if (CombatActivation.mobHitByPlayer(entity, event.getEntity(), this::isCustomMob) == null)
            return;

        // Same wake-up as walking near it: a player who stands still and fights must get the boss bar and effects too.
        wake(event.getEntity());

        double damage = 0;
        NamespacedKey damageKey = new NamespacedKey(CustomMobs.getPlugin(),
                "CustomMobs.Damage." + entity.getUniqueId());
        if (event.getEntity().getPersistentDataContainer().getKeys().contains(damageKey)) {
            damage = event.getEntity().getPersistentDataContainer().get(damageKey, PersistentDataType.DOUBLE);
        }
        damage += event.getFinalDamage();
        event.getEntity().getPersistentDataContainer().set(damageKey, PersistentDataType.DOUBLE, damage);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity))
            return;

        if (!isCustomMob(event.getEntity()))
            return;

        double healthAfter = Math.max(0, ((LivingEntity) event.getEntity()).getHealth() - event.getFinalDamage());
        double maxHealth = Objects.requireNonNull(((LivingEntity) event.getEntity())
                .getAttribute(Registry.ATTRIBUTE.get(NamespacedKey.minecraft("max_health")))).getBaseValue();
        CustomMobs.getPlugin().getCustomMobsManager().getBossBar().update(event.getEntity().getUniqueId(), healthAfter,
                maxHealth);
    }

    private final Map<UUID, Long> playerMoveCooldowns = new HashMap<>();

    @EventHandler
    public void onPlayerMove(PlayerMoveEvent event) {
        // Only trigger on meaningful movements (not only mouse)
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        // Check every 3 seconds, not more often
        Player player = event.getPlayer();
        long now = System.currentTimeMillis();
        if (playerMoveCooldowns.containsKey(player.getUniqueId())
                && now - playerMoveCooldowns.get(player.getUniqueId()) < 3000)
            return;
        playerMoveCooldowns.put(player.getUniqueId(), now);

        List<Entity> entities = event.getPlayer().getNearbyEntities(10, 5, 10);
        for (Entity entity : entities) {
            if (isCustomMob(entity))
                wake(entity);
        }
    }

    /** Boss bar, custom effects and aggressive goal of a CustomMob a player is engaging. Idempotent. */
    private void wake(Entity entity) {
        CustomMob customMob = CustomMobs.getPlugin().getCustomMobsManager()
                .getCustomMob(entity.getPersistentDataContainer().get(NamespacedKeys.KEY_NAME,
                        PersistentDataType.STRING));

        if (customMob == null) {
            entity.getPersistentDataContainer().remove(NamespacedKeys.KEY_NAME);
            return;
        }

        CustomMobCustomEffect trailEffect = customMob.getCustomMobCustomEffects().stream().filter(
                x -> x.isEnabled()
                        && x.getClass().getSimpleName().equalsIgnoreCase("Trail"))
                .findFirst().orElse(null);
        if (trailEffect != null && !Trail.activeTrails.containsKey(entity.getUniqueId()))
            Trail.activeTrails.put(entity.getUniqueId(), (Trail) trailEffect);

        CustomMobCustomEffect minerEffect = customMob.getCustomMobCustomEffects().stream().filter(
                x -> x.isEnabled()
                        && x.getClass().getSimpleName().equalsIgnoreCase("Miner"))
                .findFirst().orElse(null);
        if (minerEffect != null && !Miner.activeMiners.containsKey(entity.getUniqueId()))
            Miner.activeMiners.put(entity.getUniqueId(), (Miner) minerEffect);

        if (entity instanceof LivingEntity living && customMob.getCustomMobOption("Special") instanceof Special special)
            special.applyBossBar(living);

        customMob.enableCustomEffects(entity);

        if (entity instanceof org.bukkit.entity.Mob mob && entity.getPersistentDataContainer()
                .has(NamespacedKeys.KEY_AGGRESSIVE, PersistentDataType.BOOLEAN)) {
            AggressiveGoal.apply(mob);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!isCustomMob(event.getEntity()))
            return;

        String name = event.getEntity().getPersistentDataContainer().get(NamespacedKeys.KEY_NAME,
                PersistentDataType.STRING);
        CustomMob customMob = CustomMobs.getPlugin().getCustomMobsManager().getCustomMob(name);
        if (customMob == null)
            return;

        CustomMobs.getPlugin().getCustomMobsManager().getBossBar().deleteBossBar(event.getEntity().getUniqueId());

        // Cancel the custom effects of the CustomMob.
        customMob.cancelCustomEffects(event.getEntity().getUniqueId());

        CustomMobDeathEvent customEvent = new CustomMobDeathEvent(customMob, event);
        Bukkit.getServer().getPluginManager().callEvent(customEvent);

        for (Message message : customMob.getCustomMobMessages()) {
            if (((SpawnDeathMessage) message).isOnDeath())
                message.sendMessage(event.getEntity());
        }

        if (customMob.getCustomMobOption("Special") != null
                && !((Special) customMob.getCustomMobOption("Special")).getNaturalDrops())
            event.getDrops().clear();

        if (!customEvent.isDropsCancelled() && !customMob.getDrops().isEmpty())
            DropManager.sendDrops(customMob.getDrops(), event.getEntity());
    }

    // Menu Item deletion //
    // HIGH: protection plugins (Towny, WorldGuard) have already decided on the interaction.
    @EventHandler(priority = EventPriority.HIGH)
    public void onClickEvent(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (!action.equals(Action.RIGHT_CLICK_BLOCK) && !action.equals(Action.RIGHT_CLICK_AIR))
            return;

        if (!Objects.equals(event.getHand(), EquipmentSlot.HAND))
            return;

        RayTraceResult rayTraceResult = event.getPlayer().rayTraceBlocks(5, FluidCollisionMode.SOURCE_ONLY);
        if (event.getClickedBlock() == null && (rayTraceResult == null || rayTraceResult.getHitBlock() == null))
            return;

        Player player = event.getPlayer();
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().equals(Material.AIR))
            return;

        ItemMeta itemMeta = item.getItemMeta();
        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        if (container.getKeys().contains(NamespacedKeys.KEY_MENU_ITEM)) {
            player.getInventory().remove(item);
            CustomMobs.getPlugin().getServer().getConsoleSender().sendMessage(
                    ChatColor.translateAlternateColorCodes('&', "&c[!] CustomMobs : " + player.getName()
                            + " tried to use a CustomMob menu item and it was deleted. Please report this incident."));
        }

        if (!container.getKeys().contains(NamespacedKeys.KEY_ITEM_TYPE))
            return;

        String itemType = container.get(NamespacedKeys.KEY_ITEM_TYPE, PersistentDataType.STRING);
        boolean isItem = "Item".equalsIgnoreCase(itemType);
        boolean isSpawnerItem = "Spawner-Item".equalsIgnoreCase(itemType);
        if (!isItem && !isSpawnerItem)
            return;

        // A protection plugin denied using the item here: do nothing, and never fall back to the vanilla egg.
        boolean denied = event.useItemInHand() == Event.Result.DENY;
        event.setCancelled(true);
        if (denied)
            return;

        Manager manager = CustomMobs.getPlugin().getCustomMobsManager();
        CustomMob customMob = manager.getCustomMob(container.get(NamespacedKeys.KEY_FILE_NAME, PersistentDataType.STRING));
        if (customMob == null)
            return;

        // The item is consumed only once the spawn or the placement really happened.
        if (isItem) {
            Location blockLocation = event.getClickedBlock() != null ? event.getClickedBlock().getLocation()
                    : rayTraceResult.getHitBlock().getLocation();
            if (!customMob.allowsWorld(blockLocation.getWorld())) {
                player.sendMessage(manager.getConfigManager().getWorldNotAllowedMessage(customMob.getCustomMobFileName().replace(".yml", ""), blockLocation.getWorld().getName()));
                return;
            }
            if (customMob.spawnCustomMob(blockLocation.add(0.5, 1, 0.5), CustomMob.ORIGIN_ITEM) != null)
                consumeOne(player, item);
            return;
        }

        if (!manager.getConfigManager().getSpawnersEnabled()) {
            player.sendMessage(manager.getConfigManager().getSpawnersDisabledMessage());
            return;
        }
        Block clicked = event.getClickedBlock();
        if (clicked == null)
            return;
        Block placed = clicked.getRelative(event.getBlockFace());
        if (!placed.isReplaceable())
            return;
        if (!customMob.allowsWorld(placed.getWorld())) {
            player.sendMessage(manager.getConfigManager().getWorldNotAllowedMessage(customMob.getCustomMobFileName().replace(".yml", ""), placed.getWorld().getName()));
            return;
        }
        // Same check as a normal block placement, so Towny/WorldGuard/claims apply to CustomMobs spawners too.
        BlockPlaceEvent placeEvent = new BlockPlaceEvent(placed, placed.getState(), clicked, item, player, true, EquipmentSlot.HAND);
        Bukkit.getPluginManager().callEvent(placeEvent);
        if (placeEvent.isCancelled() || !placeEvent.canBuild())
            return;
        if (customMob.placeCustomMobSpawner(placed.getLocation()))
            consumeOne(player, item);
    }

    private static void consumeOne(Player player, ItemStack item) {
        if (player.getGameMode() != GameMode.CREATIVE)
            item.setAmount(item.getAmount() - 1);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (event.getClickedInventory() == null || event.getClickedInventory().getType() != InventoryType.PLAYER)
            return;

        ItemStack item = event.getCurrentItem();
        if (item == null)
            return;

        ItemMeta itemMeta = item.getItemMeta();
        if (itemMeta == null)
            return;

        PersistentDataContainer container = itemMeta.getPersistentDataContainer();
        if (container.getKeys().contains(NamespacedKeys.KEY_MENU_ITEM)) {
            event.setCancelled(true);
            event.getClickedInventory().removeItem(item);
            CustomMobs.getPlugin().getServer().getConsoleSender().sendMessage(
                    ChatColor.translateAlternateColorCodes('&',
                            "&c[!] CustomMobs : " + event.getWhoClicked().getName() +
                                    " had a CustomMob menu item and it was deleted. Please report this incident."));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent event) {
        if (event.getSpawner() == null)
            return;
        PersistentDataContainer container = event.getSpawner().getPersistentDataContainer();
        Location location = event.getEntity().getLocation();

        if (container.has(NamespacedKeys.KEY_SPAWNER, PersistentDataType.STRING)) {
            // A CustomMobs spawner never releases its vanilla placeholder entity.
            event.setCancelled(true);
            Manager manager = CustomMobs.getPlugin().getCustomMobsManager();
            if (!manager.getConfigManager().getSpawnersEnabled())
                return;
            String mobName = container.get(NamespacedKeys.KEY_SPAWNER, PersistentDataType.STRING);
            CustomMob customMob = manager.getCustomMob(mobName);
            if (customMob == null || customMob.getSpawner() == null)
                return;
            int range = customMob.getSpawner().getSpawnRange();

            // With this, we find the mobs of the same type. If that is ever a problem, we
            // could filter on the persistent key of the CustomMob.
            int nearbyCustomMobs = (int) event.getSpawner().getLocation().getWorld()
                    .getNearbyEntities(event.getSpawner().getLocation(), range, range, range).stream()
                    .filter(entity -> entity.getType() == customMob.getType())
                    .count();

            if (nearbyCustomMobs < customMob.getSpawner().getMaxNearbyCount()) {
                int availableSpawnCount = customMob.getSpawner().getMaxNearbyCount() - nearbyCustomMobs;
                int random = RANDOM.nextInt(Math.min(customMob.getSpawner().getSpawnCount(), availableSpawnCount)) + 1;
                for (int i = 0; i < random; i++) {
                    double offsetX = (RANDOM.nextDouble() * 2 - 1) * range;
                    double offsetZ = (RANDOM.nextDouble() * 2 - 1) * range;

                    Location spawnLocation = location.clone().add(offsetX, 0, offsetZ);
                    spawnLocation.setY(getNearestY(location.getWorld(), location.getX() + offsetX, location.getY(),
                            location.getZ() + offsetZ));

                    customMob.spawnCustomMob(spawnLocation, CustomMob.ORIGIN_SPAWNER);
                }
            }
        }
    }

    private double getNearestY(World world, double x, double y, double z) {
        Location location = new Location(world, x, y, z);

        for (int i = -2; i < 5; i++) {
            boolean blockUnder = !location.clone().add(0, i - 1, 0).getBlock().isPassable();
            boolean passable = location.clone().add(0, i, 0).getBlock().isPassable();
            boolean passableOver = location.clone().add(0, i + 1, 0).getBlock().isPassable();

            if (blockUnder && passable && passableOver) {
                return y + i;
            }
        }
        return y;
    }

    // ignoreCancelled: a break denied by a protection plugin must not drop the spawner item (duplication).
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onSpawnerBreak(BlockBreakEvent event) {
        Manager manager = CustomMobs.getPlugin().getCustomMobsManager();
        Player player = event.getPlayer();
        ItemStack itemStack = player.getInventory().getItemInMainHand();
        Block block = event.getBlock();

        if (!manager.getConfigManager().getSpawnersEnabled() || !manager.getConfigManager().getSilkSpawner()
                || block.getType() != Material.SPAWNER
                || !itemStack.getType().toString().contains("PICKAXE")) {
            return;
        }

        CreatureSpawner spawnerBlock = (CreatureSpawner) block.getState();
        PersistentDataContainer container = spawnerBlock.getPersistentDataContainer();

        if (itemStack.containsEnchantment(Enchantment.SILK_TOUCH)
                && container.has(NamespacedKeys.KEY_SPAWNER, PersistentDataType.STRING)) {
            ItemStack spawnerItem = manager.getCustomMobItem(
                    manager.getCustomMob(container.get(NamespacedKeys.KEY_SPAWNER, PersistentDataType.STRING)),
                    "spawner", 1);
            block.getLocation().getWorld().dropItem(block.getLocation(), spawnerItem);
        }
    }

    /**
     * Natural replacement. HIGH + ignoreCancelled: never decorate a spawn another plugin already cancelled,
     * and decorate before MONITOR listeners (LevelledMobs, AsketyMestieri) look at the entity.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntitySpawn(CreatureSpawnEvent event) {
        if (!SpawnRules.replaceable(event.getSpawnReason()))
            return;
        Entity entity = event.getEntity();
        if (isCustomMob(entity))
            return;
        World world = event.getLocation().getWorld();

        Map<CustomMob, Double> customMobReplacePercentages = new HashMap<>();
        double totalPercentage = 0;
        for (CustomMob customMob : CustomMobs.getPlugin().getCustomMobsManager().getCustomMobs()) {
            if (customMob.getType() != event.getEntityType())
                continue;
            // Filter before the roll, so each world keeps the chances of its own pool.
            if (!customMob.allowsWorld(world))
                continue;
            if (customMob.getCustomMobOption("Special") != null
                    && ((Special) customMob.getCustomMobOption("Special")).getReplaceNaturalPercentage() != 0.0) {
                double percentage = ((Special) customMob.getCustomMobOption("Special")).getReplaceNaturalPercentage();
                customMobReplacePercentages.put(customMob, percentage);
                totalPercentage += percentage;
            }
        }

        if (customMobReplacePercentages.isEmpty())
            return;

        double roll = Math.random() * 100;
        if (roll > totalPercentage)
            return;

        double random = Math.random() * totalPercentage;
        double cumulative = 0;
        CustomMob chosen = null;
        for (Map.Entry<CustomMob, Double> entry : customMobReplacePercentages.entrySet()) {
            cumulative += entry.getValue();
            if (random <= cumulative) {
                chosen = entry.getKey();
                break;
            }
        }

        if (chosen != null) {
            chosen.spawnCustomMob(entity, CustomMob.ORIGIN_NATURAL);
        }
    }

    /**
     * Entities that arrive already carrying a CustomMob key (summon with NBT, spawn egg with entity data,
     * schematic paste) are refused in the worlds their CustomMob does not allow.
     */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMarkedSpawn(CreatureSpawnEvent event) {
        CustomMob customMob = definitionOf(event.getEntity());
        if (customMob != null && !customMob.allowsWorld(event.getLocation().getWorld()))
            event.setCancelled(true);
    }

    // A CustomMob keeps its PDC when it changes world (portal or teleport) and no spawn event fires there.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(EntityTeleportEvent event) {
        refuseWorldChange(event);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPortal(EntityPortalEvent event) {
        refuseWorldChange(event);
    }

    private void refuseWorldChange(EntityTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || to.getWorld().equals(event.getFrom().getWorld()))
            return;
        CustomMob customMob = definitionOf(event.getEntity());
        if (customMob != null && !customMob.allowsWorld(to.getWorld()))
            event.setCancelled(true);
    }

    private CustomMob definitionOf(Entity entity) {
        String name = entity.getPersistentDataContainer().get(NamespacedKeys.KEY_NAME, PersistentDataType.STRING);
        return name == null ? null : CustomMobs.getPlugin().getCustomMobsManager().getCustomMob(name);
    }

    @EventHandler
    public void onZombieConvert(EntityTransformEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Zombie))
            return;
        if (event.getTransformReason() != EntityTransformEvent.TransformReason.DROWNED)
            return;
        if (!isCustomMob(event.getEntity()))
            return;
        event.setCancelled(true);
    }

    @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onRightClickAtEntity(org.bukkit.event.player.PlayerInteractAtEntityEvent event) {
        onRightClickEntity(event);
    }

    @EventHandler
    public void onRightClickEntity(PlayerInteractEntityEvent event) {
        Entity entity = event.getRightClicked();
        Player player = event.getPlayer();
        if (event.getHand() == EquipmentSlot.OFF_HAND)
            return;

        if (CustomMobs.getPlugin().getRideSystem() != null && isCustomMob(entity) &&
                entity.getPersistentDataContainer().has(NamespacedKeys.KEY_RIDEABLE,
                        PersistentDataType.BOOLEAN)) {
            event.setCancelled(true);
            CustomMobs.getPlugin().getRideSystem().startRiding(player, (LivingEntity) entity);
        }
    }

    private boolean isCustomMob(Entity entity) {
        return entity.getPersistentDataContainer().has(NamespacedKeys.KEY_NAME, PersistentDataType.STRING);
    }
}
