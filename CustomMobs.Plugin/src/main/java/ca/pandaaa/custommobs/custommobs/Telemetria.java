package ca.pandaaa.custommobs.custommobs;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.Writer;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import ca.pandaaa.custommobs.CustomMobs;
import ca.pandaaa.custommobs.custommobs.Events.Events;

/**
 * Temporary balance audit (Askety). While plugins/CustomMobs/telemetria.on exists, every fight between a player and a
 * CustomMob is appended to plugins/CustomMobs/telemetria/&lt;date&gt;.csv: hits taken and dealt with the real damage
 * after armor, the player's armor and Protection, fall damage and deaths right after a CustomMob, and how long each
 * CustomMob took to kill. Delete the file to stop; nothing is recorded and no file is touched while it is missing.
 */
public class Telemetria implements Listener {
    static final String HEADER = "ts,evento,mob,mob_uuid,giocatore,causa,danno_base,danno_finale,armatura,robustezza,protezione,vita_prima,vita_max,extra";
    /** Fall damage or a death this soon after touching a CustomMob is blamed on it (Charge launches, knockback). */
    static final long FINESTRA_MS = 10_000;

    private static final ExecutorService SCRITTURA = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "CustomMobs-telemetria");
        t.setDaemon(true);
        return t;
    });
    private static final Map<UUID, Contatto> ULTIMO_CONTATTO = new HashMap<>();
    private static final Map<UUID, Scontro> SCONTRI = new HashMap<>();
    private static long controllatoIl;
    private static boolean attiva;

    record Contatto(String mob, UUID mobId, long quando) { }
    static final class Scontro { long primoColpo; int colpi; double danno; }

    /** Called by Charge when it launches a player: the fall that follows has no damager of its own. */
    public static void contatto(Player player, Entity mob) {
        if (!attiva()) return;
        String nome = nome(mob);
        if (nome != null)
            ULTIMO_CONTATTO.put(player.getUniqueId(), new Contatto(nome, mob.getUniqueId(), System.currentTimeMillis()));
    }

    static boolean recente(Contatto c, long ora) {
        return c != null && ora - c.quando() <= FINESTRA_MS;
    }

    // ponytail: file check cached 5 s, a config key would need a reload to turn off
    private static boolean attiva() {
        if (CustomMobs.getPlugin() == null) return false; // unit tests: no plugin, no audit
        long ora = System.currentTimeMillis();
        if (ora - controllatoIl > 5_000) {
            attiva = new File(CustomMobs.getPlugin().getDataFolder(), "telemetria.on").exists();
            controllatoIl = ora;
        }
        return attiva;
    }

    private static String nome(Entity entity) {
        if (entity == null) return null;
        return entity.getPersistentDataContainer().get(NamespacedKeys.KEY_NAME, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void colpo(EntityDamageByEntityEvent event) {
        if (!attiva()) return;
        Entity attaccante = Events.attacker(event.getDamager());
        String mob = nome(attaccante);
        if (mob != null && event.getEntity() instanceof Player player) {
            ULTIMO_CONTATTO.put(player.getUniqueId(), new Contatto(mob, attaccante.getUniqueId(), System.currentTimeMillis()));
            scrivi("colpo", mob, attaccante.getUniqueId(), player, event.getCause().name(), event.getDamage(), event.getFinalDamage(), stato(player));
            return;
        }
        // Lightning and other damage whose damager is not the mob itself, right after a CustomMob (not PvP).
        if (event.getEntity() instanceof Player player && !(attaccante instanceof Player)) {
            Contatto c = ULTIMO_CONTATTO.get(player.getUniqueId());
            if (recente(c, System.currentTimeMillis()))
                scrivi("dopo_mob", c.mob(), c.mobId(), player, event.getCause().name(), event.getDamage(), event.getFinalDamage(), stato(player));
            return;
        }
        String colpito = nome(event.getEntity());
        if (colpito != null && attaccante instanceof Player player && event.getEntity() instanceof LivingEntity vittima) {
            // ponytail: mobs that despawn mid-fight never reach mobMorto; a hard cap keeps the map small
            if (SCONTRI.size() > 1000) SCONTRI.clear();
            Scontro s = SCONTRI.computeIfAbsent(vittima.getUniqueId(), k -> new Scontro());
            if (s.colpi++ == 0) s.primoColpo = System.currentTimeMillis();
            s.danno += event.getFinalDamage();
            ItemStack arma = player.getInventory().getItemInMainHand();
            scrivi("mob_colpito", colpito, vittima.getUniqueId(), player, event.getCause().name(), event.getDamage(), event.getFinalDamage(),
                    arma.getType().name() + " sharp" + arma.getEnchantmentLevel(Enchantment.SHARPNESS) + " vita_mob " + Math.round(vittima.getHealth() - event.getFinalDamage()));
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void altroDanno(EntityDamageEvent event) {
        if (event instanceof EntityDamageByEntityEvent || !(event.getEntity() instanceof Player player) || !attiva()) return;
        Contatto c = ULTIMO_CONTATTO.get(player.getUniqueId());
        if (recente(c, System.currentTimeMillis()))
            scrivi("dopo_mob", c.mob(), c.mobId(), player, event.getCause().name(), event.getDamage(), event.getFinalDamage(), stato(player));
    }

    /** Shield raised and Resistance level: a blocked or resisted hit must not read as a weak mob. */
    private static String stato(Player p) {
        org.bukkit.potion.PotionEffect res = p.getPotionEffect(org.bukkit.potion.PotionEffectType.RESISTANCE);
        return (p.isBlocking() ? "scudo " : "") + (res == null ? "" : "res" + (res.getAmplifier() + 1));
    }

    /** Miner: every dug or refused block, to check graves and containers stay untouched. */
    public static void scavo(Entity mob, org.bukkit.block.Block block, boolean rotto) {
        if (!attiva()) return;
        String nome = nome(mob);
        if (nome == null) return;
        writeRaw(riga(System.currentTimeMillis(), rotto ? "scavo" : "scavo_negato", nome, mob.getUniqueId(), "", block.getType().name(),
                0, 0, 0, 0, 0, 0, 0, block.getWorld().getName() + " " + block.getX() + " " + block.getY() + " " + block.getZ()));
    }

    /** Every 60 s, how many CustomMobs (per name) and vanilla monsters are loaded in each world with players. */
    private static org.bukkit.scheduler.BukkitTask censimento;

    public static void avviaCensimento(org.bukkit.plugin.Plugin plugin) {
        if (censimento != null) censimento.cancel(); // cm reload registers the listeners again
        censimento = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            if (!attiva()) return;
            long ora = System.currentTimeMillis();
            for (org.bukkit.World w : plugin.getServer().getWorlds()) {
                if (w.getPlayers().isEmpty()) continue;
                Map<String, Integer> conta = new HashMap<>();
                int vanilla = 0;
                for (LivingEntity e : w.getLivingEntities()) {
                    String n = nome(e);
                    if (n != null) conta.merge(n, 1, Integer::sum);
                    else if (e instanceof org.bukkit.entity.Enemy) vanilla++;
                }
                writeRaw(riga(ora, "censimento", "VANILLA", null, "", w.getName(), vanilla, 0, 0, 0, 0, 0, 0,
                        "giocatori " + w.getPlayers().size()));
                for (Map.Entry<String, Integer> c : conta.entrySet())
                    writeRaw(riga(ora, "censimento", c.getKey(), null, "", w.getName(), c.getValue(), 0, 0, 0, 0, 0, 0, ""));
            }
        }, 1200L, 1200L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void morte(PlayerDeathEvent event) {
        if (!attiva()) return;
        Player player = event.getEntity();
        Contatto c = ULTIMO_CONTATTO.remove(player.getUniqueId());
        if (!recente(c, System.currentTimeMillis())) return;
        EntityDamageEvent ultimo = player.getLastDamageCause();
        scrivi("morte", c.mob(), c.mobId(), player, ultimo == null ? "?" : ultimo.getCause().name(), 0, 0, "");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void mobMorto(EntityDeathEvent event) {
        String mob = nome(event.getEntity());
        Scontro s = mob == null ? null : SCONTRI.remove(event.getEntity().getUniqueId());
        if (s == null || !attiva()) return;
        Player killer = event.getEntity().getKiller();
        writeRaw(riga(System.currentTimeMillis(), "mob_morto", mob, event.getEntity().getUniqueId(), killer == null ? "" : killer.getName(), "",
                0, s.danno, 0, 0, 0, 0, 0, "secondi " + (System.currentTimeMillis() - s.primoColpo) / 1000.0 + " colpi " + s.colpi));
    }

    private static void scrivi(String evento, String mob, UUID mobId, Player p, String causa, double base, double finale, String extra) {
        writeRaw(riga(System.currentTimeMillis(), evento, mob, mobId, p.getName(), causa, base, finale,
                valore(p, "armor"), valore(p, "armor_toughness"), protezione(p), p.getHealth(), valore(p, "max_health"), extra));
    }

    private static double valore(Player p, String attributo) {
        Attribute a = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(attributo));
        AttributeInstance i = a == null ? null : p.getAttribute(a);
        return i == null ? 0 : i.getValue();
    }

    private static int protezione(Player p) {
        int livelli = 0;
        for (ItemStack pezzo : p.getInventory().getArmorContents())
            if (pezzo != null) livelli += pezzo.getEnchantmentLevel(Enchantment.PROTECTION);
        return livelli;
    }

    static String riga(long ts, String evento, String mob, UUID mobId, String giocatore, String causa, double base, double finale,
                       double armatura, double robustezza, int protezione, double vitaPrima, double vitaMax, String extra) {
        return String.join(",", String.valueOf(ts), evento, mob, String.valueOf(mobId), giocatore, causa,
                d(base), d(finale), d(armatura), d(robustezza), String.valueOf(protezione), d(vitaPrima), d(vitaMax),
                extra.replace(",", ";"));
    }

    private static String d(double v) {
        return String.valueOf(Math.round(v * 100) / 100.0);
    }

    private static void writeRaw(String riga) {
        File cartella = new File(CustomMobs.getPlugin().getDataFolder(), "telemetria");
        SCRITTURA.execute(() -> {
            File file = new File(cartella, LocalDate.now() + ".csv");
            try {
                cartella.mkdirs();
                boolean nuovo = !file.exists();
                try (Writer w = new FileWriter(file, true)) {
                    if (nuovo) w.write(HEADER + "\n");
                    w.write(riga + "\n");
                }
            } catch (IOException e) {
                CustomMobs.getPlugin().getLogger().warning("Telemetria: " + e.getMessage());
            }
        });
    }
}
