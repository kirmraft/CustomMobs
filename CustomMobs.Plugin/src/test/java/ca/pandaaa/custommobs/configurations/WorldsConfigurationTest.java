package ca.pandaaa.custommobs.configurations;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;

import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import ca.pandaaa.custommobs.custommobs.WorldPolicy;

class WorldsConfigurationTest {

    private static WorldPolicy policyOf(String yaml) throws InvalidConfigurationException {
        YamlConfiguration configuration = new YamlConfiguration();
        configuration.loadFromString(yaml);
        return new CustomMobConfiguration(configuration, new File("zombie_corrotto.yml")).getWorldPolicy();
    }

    @Test
    void missingWorldsMeansAllWorlds() throws Exception {
        WorldPolicy policy = policyOf("mob:\n  type: ZOMBIE\n");
        assertFalse(policy.isRestricted());
        assertTrue(policy.allows("world"));
    }

    @Test
    void emptyWorldsMeansNoWorld() throws Exception {
        WorldPolicy policy = policyOf("mob:\n  type: ZOMBIE\nworlds: []\n");
        assertFalse(policy.allows("world"));
        assertFalse(policy.allows("world_askety_risorse"));
    }

    @Test
    void worldsListIsRead() throws Exception {
        WorldPolicy policy = policyOf("mob:\n  type: ZOMBIE\nworlds:\n  - world_askety_risorse\n");
        assertTrue(policy.allows("world_askety_risorse"));
        assertFalse(policy.allows("world"));
    }
}
