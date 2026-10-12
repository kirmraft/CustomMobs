package ca.pandaaa.custommobs.custommobs.CustomEffects;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.Event;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import ca.pandaaa.custommobs.api.TargetVerdict;
import ca.pandaaa.custommobs.configurations.CustomMobConfiguration;
import ca.pandaaa.custommobs.custommobs.TargetPolicies;
import ca.pandaaa.custommobs.custommobs.TargetRequests;

/**
 * Miner picks its own target: it must respect the policy and other plugins' vetoes like the aggressive goal. It digs
 * until a walkable 2-high corridor reaches the target, one block per cooldown, only within reach.
 */
class MinerTest {
    private final java.util.function.Consumer<Event> original = CustomMobCustomEffect.dispatcher;
    private final java.util.function.Consumer<Event> originalTargets = TargetRequests.dispatcher;
    private final java.util.function.LongSupplier originalClock = Miner.clock;
    private final java.util.function.Predicate<Block> originalMayBreak = Miner.mayBreak;
    private final java.util.function.Predicate<Block> originalHoldsData = Miner.holdsData;
    private final java.util.function.BiPredicate<org.bukkit.entity.Mob, Block> originalOthersAllow = Miner.othersAllow;
    private final List<String> asked = new ArrayList<>();
    private final List<Player> nearby = new ArrayList<>();
    private final Set<Player> vetoed = new HashSet<>();
    private final Map<String, Material> solid = new HashMap<>();
    private final Map<String, Block> blocks = new HashMap<>();
    private final List<String> broken = new ArrayList<>();
    private final World world = world();
    private LivingEntity target;
    private double mobX = 0.5;
    private double mobY = 64;
    private long now = 1_000_000;

    private final Zombie mob = (Zombie) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Zombie.class},
            (self, m, a) -> switch (m.getName()) {
                case "getNearbyEntities" -> new ArrayList<Entity>(nearby);
                case "getLocation" -> new Location(world, mobX, mobY, 0.5);
                case "getEyeLocation" -> new Location(world, mobX, 65.74, 0.5);
                case "getTarget" -> target;
                case "setTarget" -> {
                    target = (LivingEntity) a[0]; // Paper: no EntityTargetEvent from Mob#setTarget
                    yield null;
                }
                case "equals" -> self == a[0];
                case "hashCode" -> System.identityHashCode(self);
                default -> throw new UnsupportedOperationException("mob." + m.getName());
            });

    MinerTest() {
        CustomMobCustomEffect.dispatcher = e -> { };
        TargetRequests.dispatcher = e -> {
            EntityTargetLivingEntityEvent event = (EntityTargetLivingEntityEvent) e;
            if (vetoed.contains(event.getTarget()))
                event.setCancelled(true); // like Origini
        };
        Miner.clock = () -> now;
        Miner.mayBreak = block -> true;
        Miner.holdsData = block -> false;
        Miner.othersAllow = (m, block) -> {
            asked.add(block.toString());
            return m == mob;
        };
    }

    @AfterEach
    void restore() {
        CustomMobCustomEffect.dispatcher = original;
        TargetRequests.dispatcher = originalTargets;
        Miner.clock = originalClock;
        Miner.mayBreak = originalMayBreak;
        Miner.holdsData = originalHoldsData;
        Miner.othersAllow = originalOthersAllow;
        TargetPolicies.set(null);
    }

    private static String key(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    /** Solid blocks at x for y 64 (feet) and 65 (head), z 0. */
    private void wall(Material material, int... xs) {
        for (int x : xs) {
            solid.put(key(x, 64, 0), material);
            solid.put(key(x, 65, 0), material);
        }
    }

    private Block block(int x, int y, int z) {
        return blocks.computeIfAbsent(key(x, y, z), k -> (Block) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[] {Block.class}, (self, m, a) -> switch (m.getName()) {
                    case "isPassable" -> !solid.containsKey(k);
                    case "isEmpty" -> !solid.containsKey(k);
                    case "getType" -> solid.getOrDefault(k, Material.AIR);
                    case "getLocation" -> new Location(world, x, y, z);
                    case "getWorld" -> world;
                    case "breakNaturally" -> {
                        solid.remove(k);
                        broken.add(k);
                        yield true;
                    }
                    case "equals" -> self == a[0];
                    case "hashCode" -> System.identityHashCode(self);
                    case "toString" -> k;
                    default -> throw new UnsupportedOperationException("block." + m.getName());
                }));
    }

    private World world() {
        return (World) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {World.class},
                (self, m, a) -> switch (m.getName()) {
                    case "getBlockAt" -> a.length == 1
                            ? block(((Location) a[0]).getBlockX(), ((Location) a[0]).getBlockY(), ((Location) a[0]).getBlockZ())
                            : block((Integer) a[0], (Integer) a[1], (Integer) a[2]);
                    case "equals" -> self == a[0];
                    case "hashCode" -> System.identityHashCode(self);
                    default -> throw new UnsupportedOperationException("world." + m.getName());
                });
    }

    private Player player(String name, double x) {
        Player p = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
                (self, m, a) -> switch (m.getName()) {
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "getLocation" -> new Location(world, x, 64, 0.5);
                    case "getEyeLocation" -> new Location(world, x, 65.62, 0.5);
                    case "equals" -> self == a[0];
                    case "hashCode" -> System.identityHashCode(self);
                    case "toString" -> name;
                    default -> throw new UnsupportedOperationException(name + "." + m.getName());
                });
        nearby.add(p);
        return p;
    }

    private Miner miner() {
        return new Miner(new CustomMobConfiguration(new YamlConfiguration(), new File("t.yml")));
    }

    /** Triggers the effect {@code times} times, letting the 750 ms dig cooldown elapse between them. */
    private void trigger(Miner miner, int times) {
        for (int i = 0; i < times; i++) {
            miner.triggerCustomEffect(mob);
            now += 750;
        }
    }

    @Test
    void vetoedTargetIsNotDugTowards() {
        vetoed.add(player("non_morto", 3.5));
        wall(Material.STONE, 1);
        assertNull(miner().triggerCustomEffect(mob));
        assertTrue(broken.isEmpty());
        assertNull(target);
    }

    @Test
    void deniedPlayerIsNotChosen() {
        Player denied = player("denied", 3.5);
        wall(Material.STONE, 1);
        TargetPolicies.set((m, p) -> p == denied ? TargetVerdict.DENY : TargetVerdict.ALLOW);
        assertNull(miner().triggerCustomEffect(mob));
        assertNull(target);
        assertTrue(broken.isEmpty());
    }

    @Test
    void allowedPlayerIsTargetedAndDugTowards() {
        Player p = player("p", 3.5);
        wall(Material.STONE, 1);
        assertNull(miner().triggerCustomEffect(mob));
        assertSame(p, target);
        assertEquals(1, broken.size());
    }

    @Test
    void twoThickWallIsDugIntoAWalkableTwoHighCorridorThenDiggingStops() {
        player("p", 4.5);
        wall(Material.STONE, 1, 2);
        trigger(miner(), 10);
        assertEquals(Set.of(key(1, 64, 0), key(1, 65, 0), key(2, 64, 0), key(2, 65, 0)), new HashSet<>(broken));
        assertEquals(4, broken.size(), "each block once, nothing else");
    }

    @Test
    void oneHighPassageIsRaisedToTwoHigh() {
        player("p", 4.5);
        for (int x = 1; x <= 3; x++)
            solid.put(key(x, 65, 0), Material.STONE); // ceiling at head height: only crawlable
        Miner miner = miner();
        trigger(miner, 10);
        assertEquals(Set.of(key(1, 65, 0), key(2, 65, 0)), new HashSet<>(broken), "only what is within reach");
        mobX = 2.5; // walked into the raised part
        trigger(miner, 10);
        assertEquals(Set.of(key(1, 65, 0), key(2, 65, 0), key(3, 65, 0)), new HashSet<>(broken));
    }

    @Test
    void oneBlockPerCooldown() {
        player("p", 4.5);
        wall(Material.STONE, 1, 2);
        Miner miner = miner();
        miner.triggerCustomEffect(mob);
        miner.triggerCustomEffect(mob);
        now += 749;
        miner.triggerCustomEffect(mob);
        assertEquals(1, broken.size());
    }

    @Test
    void blocksOutOfReachAreNotDugUntilTheMobComesCloser() {
        player("p", 8.5);
        wall(Material.STONE, 5);
        Miner miner = miner();
        trigger(miner, 5);
        assertTrue(broken.isEmpty(), "no digging from afar");
        mobX = 3.5; // the mob walked up the open side of the corridor
        trigger(miner, 5);
        assertEquals(Set.of(key(5, 64, 0), key(5, 65, 0)), new HashSet<>(broken));
    }

    @Test
    void openCorridorDigsNothing() {
        player("p", 4.5);
        solid.put(key(2, 66, 0), Material.STONE); // above the corridor: not in the way
        solid.put(key(2, 63, 0), Material.STONE); // floor
        trigger(miner(), 5);
        assertTrue(broken.isEmpty());
    }

    @Test
    void unbreakableOrProtectedBlockStopsTheDigWithoutBreakingAnythingElse() {
        player("p", 4.5);
        wall(Material.BEDROCK, 1);
        trigger(miner(), 5);
        assertTrue(broken.isEmpty());

        solid.clear();
        wall(Material.STONE, 1);
        Miner.mayBreak = block -> false; // mob_griefing off or WorldGuard region
        trigger(miner(), 5);
        assertTrue(broken.isEmpty());
    }

    @Test
    void blockHoldingDataIsNeverDugSoAGraveChestKeepsItsItems() {
        player("p", 4.5);
        wall(Material.CHEST, 1);
        Miner.holdsData = block -> block.getType() == Material.CHEST; // chest, barrel, grave, sign, spawner...
        trigger(miner(), 5);
        assertTrue(broken.isEmpty());
        assertTrue(asked.isEmpty(), "a container is refused before other plugins are even asked");
    }

    @Test
    void anotherPluginRefusingTheChangeStopsTheDig() {
        player("p", 4.5);
        wall(Material.STONE, 1);
        Miner.othersAllow = (m, block) -> false; // Towny claim, BlockLocker, a grave plugin
        trigger(miner(), 5);
        assertTrue(broken.isEmpty());
    }

    @Test
    void otherPluginsAreAskedWithTheDiggingMobBeforeEachBreak() {
        player("p", 4.5);
        wall(Material.STONE, 1);
        trigger(miner(), 5);
        assertEquals(Set.of(key(1, 64, 0), key(1, 65, 0)), new HashSet<>(broken));
        assertEquals(broken, asked, "every broken block was approved first, for this mob");
    }

    @Test
    void mobStandingOnASlabDoesNotDigItsOwnFloor() {
        mobY = 64.5;
        solid.put(key(0, 64, 0), Material.STONE); // the slab it stands on, row of slabs up to the player
        for (int x = 1; x <= 4; x++)
            solid.put(key(x, 64, 0), Material.STONE);
        Player p = (Player) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] {Player.class},
                (self, m, a) -> switch (m.getName()) {
                    case "getGameMode" -> GameMode.SURVIVAL;
                    case "getLocation" -> new Location(world, 4.5, 64.5, 0.5);
                    case "equals" -> self == a[0];
                    case "hashCode" -> System.identityHashCode(self);
                    default -> throw new UnsupportedOperationException("p." + m.getName());
                });
        nearby.add(p);
        trigger(miner(), 5);
        assertTrue(broken.isEmpty());
    }
}
