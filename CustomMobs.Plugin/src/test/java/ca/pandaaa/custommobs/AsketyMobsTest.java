package ca.pandaaa.custommobs;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import java.io.FileReader;
import java.io.Reader;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/** The Askety pool (askety/Mobs) must stay locked to the resource world. */
class AsketyMobsTest {

    @Test
    @SuppressWarnings("unchecked")
    void everyAsketyMobIsLimitedToTheResourceWorld() throws Exception {
        File[] files = new File("askety/Mobs").listFiles((dir, name) -> name.endsWith(".yml"));
        assertNotNull(files);
        assertEquals(3, files.length);
        for (File file : files) {
            try (Reader reader = new FileReader(file)) {
                Map<String, Object> mob = new Yaml().load(reader);
                assertEquals(List.of("world_askety_risorse"), mob.get("worlds"), file.getName());
                Map<String, Object> special = (Map<String, Object>) mob.get("special");
                assertTrue(((Number) special.get("replace-natural")).doubleValue() > 0, file.getName());
                assertNull(mob.get("spawner"), file.getName());
            }
        }
    }
}
