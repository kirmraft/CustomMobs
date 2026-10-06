package ca.pandaaa.custommobs.utils;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class VersionCompareTest {

    @Test
    void paperBuildVersionIsParsed() {
        // Paper 26.2: Bukkit.getBukkitVersion() = "26.2.build.92-stable"
        assertEquals(0, Utils.compareVersions("26.2.build.92-stable", "26.2"));
        assertTrue(Utils.compareVersions("26.2.build.92-stable", "26.1") > 0);
        assertTrue(Utils.compareVersions("26.2.build.92-stable", "26.3") < 0);
    }

    @Test
    void spigotVersionIsParsed() {
        assertEquals(0, Utils.compareVersions("26.2-R0.1-SNAPSHOT", "26.2"));
        assertTrue(Utils.compareVersions("1.21.11-R0.1-SNAPSHOT", "1.21.4") > 0);
        assertTrue(Utils.compareVersions("1.21.4-R0.1-SNAPSHOT", "1.21.11") < 0);
    }

    @Test
    void missingPartsCountAsZero() {
        assertEquals(0, Utils.compareVersions("1.21-R0.1-SNAPSHOT", "1.21.0"));
        assertTrue(Utils.compareVersions("26.1.2.build.74-stable", "26.1") > 0);
    }
}
