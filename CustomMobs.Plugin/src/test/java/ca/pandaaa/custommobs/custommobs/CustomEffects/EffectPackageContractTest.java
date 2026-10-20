package ca.pandaaa.custommobs.custommobs.CustomEffects;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Modifier;

import org.junit.jupiter.api.Test;

import com.google.common.reflect.ClassPath;

import ca.pandaaa.custommobs.configurations.CustomMobConfiguration;

/**
 * CustomMobConfiguration instantiates every top-level class of this package as an effect, inside one try: a helper
 * class here (AttesaPerMob, 20-10) threw NoSuchMethodException and stopped the effects of all 331 mobs from loading.
 */
class EffectPackageContractTest {
    @Test
    void everyClassOfThePackageIsALoadableEffect() throws Exception {
        for (ClassPath.ClassInfo info : ClassPath.from(getClass().getClassLoader())
                .getTopLevelClassesRecursive("ca.pandaaa.custommobs.custommobs.CustomEffects")) {
            Class<?> c = info.load();
            if (c.getSimpleName().endsWith("Test") || c.isEnum() || Modifier.isAbstract(c.getModifiers()))
                continue;
            assertTrue(CustomMobCustomEffect.class.isAssignableFrom(c), c.getSimpleName() + " is not an effect: move it out of this package");
            assertDoesNotThrow(() -> c.getDeclaredConstructor(CustomMobConfiguration.class), c.getSimpleName());
        }
    }
}
