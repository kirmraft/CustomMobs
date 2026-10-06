package ca.pandaaa.custommobs.custommobs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class WorldPolicyTest {

    @Test
    void absentFieldKeepsLegacyBehaviourEverywhere() {
        WorldPolicy policy = WorldPolicy.of(null);
        assertTrue(policy.allows("world"));
        assertTrue(policy.allows("world_askety_risorse"));
        assertFalse(policy.isRestricted());
    }

    @Test
    void emptyListAllowsNoWorld() {
        WorldPolicy policy = WorldPolicy.of(List.of());
        assertFalse(policy.allows("world"));
        assertFalse(policy.allows("world_askety_risorse"));
        assertTrue(policy.isRestricted());
    }

    @Test
    void listAllowsOnlyItsWorlds() {
        WorldPolicy policy = WorldPolicy.of(List.of("world_askety_risorse"));
        assertTrue(policy.allows("world_askety_risorse"));
        assertFalse(policy.allows("world"));
        assertFalse(policy.allows("world_nether"));
    }

    @Test
    void worldNamesAreCaseInsensitiveLikeBukkitGetWorld() {
        WorldPolicy policy = WorldPolicy.of(List.of("World_Askety_Risorse"));
        assertTrue(policy.allows("world_askety_risorse"));
    }

    @Test
    void nullWorldIsNeverAllowedWhenRestricted() {
        assertFalse(WorldPolicy.of(List.of("world")).allows(null));
        assertTrue(WorldPolicy.of(null).allows(null));
    }

    @Test
    void describeShowsTheRule() {
        assertEquals("all worlds", WorldPolicy.of(null).describe());
        assertEquals("no world", WorldPolicy.of(List.of()).describe());
        assertEquals("[world_askety_risorse]", WorldPolicy.of(List.of("world_askety_risorse")).describe());
    }
}
