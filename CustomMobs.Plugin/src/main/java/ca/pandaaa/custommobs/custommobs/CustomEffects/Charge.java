package ca.pandaaa.custommobs.custommobs.CustomEffects;

import java.util.ArrayList;
import java.util.List;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import ca.pandaaa.custommobs.configurations.CustomMobConfiguration;
import ca.pandaaa.custommobs.custommobs.CustomMob;
import ca.pandaaa.custommobs.guis.BasicTypes.DoubleGUI;
import ca.pandaaa.custommobs.guis.EditCustomMobs.CustomEffects.CustomEffectOptionsGUI;
import ca.pandaaa.custommobs.utils.CustomMobsItem;
import ca.pandaaa.custommobs.utils.Utils;

/**
 * Pushes the players back (and in the air) when they enter in collision with the CustomMob.
 */
public class Charge extends CustomMobCustomEffect {

    /**
     * Determines the strength of the "charge" effect, which indicates how far the players will be pushed back
     * upon impact with the CustomMob.
     * @minimum 0.5
     * @maximum 3
     */
    private static final String KNOCKBACK_STRENGTH = "custom-effects.charge.knockback-strength";
    private double knockbackStrength;

    /**
     * Seconds a CustomMob waits after a charge before it can charge again. ON_IMPACT effects run every 0.5 s while a
     * player is next to the mob: without a wait the player is launched again and again and dies of fall damage.
     * 0 keeps that old behaviour.
     */
    private static final String COOLDOWN = "custom-effects.charge.cooldown";
    private final int cooldown;

    /** Replaced in tests: wall-clock time. */
    static java.util.function.LongSupplier clock = System::currentTimeMillis;
    // One Charge per CustomMob type, shared by its mobs: the wait is per mob.
    private final java.util.Map<java.util.UUID, Long> nextCharge = new java.util.HashMap<>();

    public Charge(CustomMobConfiguration mobConfiguration) {
        super(mobConfiguration, CustomEffectType.ON_IMPACT);
        this.knockbackStrength = getCustomEffectOption(KNOCKBACK_STRENGTH, Double.class, 1D);
        this.cooldown = Math.max(0, getCustomEffectOption(COOLDOWN, Integer.class, 0));
    }

    public List<Player> triggerCustomEffect(Entity entity) {
        long now = clock.getAsLong();
        Long next = nextCharge.get(entity.getUniqueId());
        if (next != null && now < next)
            return new ArrayList<>();

        List<Entity> playersAround = entity.getNearbyEntities(1D, 1D, 1D).stream().filter(e -> e instanceof Player).toList();

        List<Player> affectedPlayers = new ArrayList<>();
        for (Entity player : playersAround) {
            if (triggerCustomEffectCancelled(entity, (Player) player, this)) continue;
            player.setVelocity(player.getLocation().getDirection().multiply(knockbackStrength * -1).setY(knockbackStrength));
            affectedPlayers.add((Player) player);
        }
        if (cooldown > 0 && !affectedPlayers.isEmpty()) {
            // Expired waits are dropped here, so dead mobs do not stay in the map.
            nextCharge.values().removeIf(time -> time <= now);
            nextCharge.put(entity.getUniqueId(), now + cooldown * 1000L);
        }
        return affectedPlayers;
    }

    public ItemStack modifyOption(Player clicker, CustomMob customMob, String option, ClickType clickType) {
        switch (option.toLowerCase()) {
            case "knockbackstrength": {
                if (clickType.isRightClick()) {
                    this.knockbackStrength = 1;
                    setCustomEffectOption(KNOCKBACK_STRENGTH, this.knockbackStrength);
                } else {
                    new DoubleGUI("Knockback strength", false, 0.5, 3, (value) -> {
                        this.knockbackStrength = value;
                        setCustomEffectOption(KNOCKBACK_STRENGTH, this.knockbackStrength);
                        new CustomEffectOptionsGUI(customMob, this, getOptionsItems()).openInventory(clicker);
                    }).openInventory(clicker, knockbackStrength);
                }
                return getCustomEffectOptionItemStack(getKnockbackStrengthItem(), true);
            }
            default:
                return handleMessageOption(clicker, customMob, option, clickType);
        }
    }

    public ItemStack getCustomEffectItem() {
        CustomMobsItem item = new CustomMobsItem(Material.GOAT_HORN);
        item.setName("&6&lCharge");
        String charge = this.enabled ? "&a&lOn" : "&c&lOff";
        item.addLore("&eCharge: &f" + charge);
        item.addLore("&bType: &f" + Utils.getSentenceCase(this.customEffectType.name()));
        item.addLore("", "&7&o(( CustomMob makes players jump back on impact ))");
        item.setCustomEffectPersistentDataContainer(this.getClass().getSimpleName());
        return getCustomEffectItemStack(item, this.getClass().getSimpleName());
    }

    public List<ItemStack> getOptionsItems() {
        List<ItemStack> items = new ArrayList<>();
        items.add(getCustomEffectOptionItemStack(getKnockbackStrengthItem(), true));
        items.add(getMessageItem().getItem());
        return items;
    }

    private CustomMobsItem getKnockbackStrengthItem() {
        CustomMobsItem item = new CustomMobsItem(Material.STICK);
        item.addEnchantment(Enchantment.KNOCKBACK, 1, true);
        item.setName("&a&lKnockback strength");
        item.addLore("&eKnockback strength: &f" + this.knockbackStrength);
        item.setCustomEffectPersistentDataContainer(this.getClass().getSimpleName() + ".KnockbackStrength");
        return item;
    }

    public double getKnockbackStrength() {
        return knockbackStrength;
    }
}
