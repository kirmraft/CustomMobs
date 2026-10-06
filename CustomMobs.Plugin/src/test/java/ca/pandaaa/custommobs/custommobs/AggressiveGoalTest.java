package ca.pandaaa.custommobs.custommobs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.bukkit.entity.Player;
import org.junit.jupiter.api.Test;

import ca.pandaaa.custommobs.api.MobTargetPolicy;
import ca.pandaaa.custommobs.api.TargetVerdict;

class AggressiveGoalTest {
    private final Scene scene = new Scene();

    private AggressiveGoal goal(double followRange, MobTargetPolicy policy) {
        return new AggressiveGoal(scene.mob, () -> followRange, () -> policy);
    }

    private void tick(AggressiveGoal goal, int times) {
        for (int i = 0; i < times; i++)
            goal.tick();
    }

    @Test
    void withoutPolicyChasesAndHitsTheNearestPlayer() {
        Player near = scene.player("near", 1);
        scene.player("far", 10);
        tick(goal(32, TargetPolicies.ALLOW_ALL), 1);
        assertEquals(List.of(near), scene.attacked);
    }

    @Test
    void deniedPlayerIsNeverChasedNorHit() {
        Player denied = scene.player("denied", 1);
        tick(goal(32, (mob, p) -> p == denied ? TargetVerdict.DENY : TargetVerdict.ALLOW), 5);
        assertTrue(scene.attacked.isEmpty());
        assertTrue(scene.chased.isEmpty());
        assertNull(scene.target);
    }

    @Test
    void externalVetoIsNotBypassed() {
        Player ignoredByOrigini = scene.player("non_morto", 1);
        scene.vetoed.add(ignoredByOrigini);
        tick(goal(32, TargetPolicies.ALLOW_ALL), 5);
        assertTrue(scene.attacked.isEmpty(), "aggressive:true must not hit a vetoed player");
        assertTrue(scene.chased.isEmpty(), "aggressive:true must not chase a vetoed player");
    }

    @Test
    void cancelledTargetIsNotRetriedEveryTick() {
        Player vetoedPlayer = scene.player("vetoed", 1);
        scene.vetoed.add(vetoedPlayer);
        tick(goal(32, TargetPolicies.ALLOW_ALL), 10);
        assertTrue(scene.setTargetCalls.size() <= 1, "setTarget retried " + scene.setTargetCalls.size() + " times");
    }

    @Test
    void afterAVetoTheNextAllowedPlayerIsTaken() {
        Player vetoedPlayer = scene.player("vetoed", 1);
        Player other = scene.player("other", 1.5);
        scene.vetoed.add(vetoedPlayer);
        tick(goal(32, TargetPolicies.ALLOW_ALL), 3);
        assertEquals(other, scene.target);
        assertFalse(scene.attacked.contains(vetoedPlayer));
        assertTrue(scene.attacked.contains(other));
    }

    @Test
    void retargetRespectsADenyOnTheCurrentTarget() {
        Player current = scene.player("current", 1);
        scene.target = current; // e.g. set by vanilla AI before a policy said no
        tick(goal(32, (mob, p) -> TargetVerdict.DENY), 3);
        assertTrue(scene.attacked.isEmpty());
        assertTrue(scene.chased.isEmpty());
    }

    @Test
    void priorityPlayerIsChasedOverANearerOne() {
        scene.player("near", 10);
        Player prio = scene.player("prio", 13);
        tick(goal(32, (mob, p) -> p == prio ? TargetVerdict.prefer(1.4) : TargetVerdict.ALLOW), 1);
        assertEquals(prio, scene.target);
        assertEquals(List.of(prio), scene.chased);
    }

    @Test
    void followRangeBelowAndAbove32() {
        scene.player("p", 20);
        tick(goal(16, TargetPolicies.ALLOW_ALL), 1);
        assertTrue(scene.chased.isEmpty(), "20 blocks away is outside a 16 follow range");

        Scene far = new Scene();
        Player p = far.player("p", 40);
        new AggressiveGoal(far.mob, () -> 48, () -> TargetPolicies.ALLOW_ALL).tick();
        assertEquals(List.of(p), far.chased, "40 blocks away is inside a 48 follow range");
    }

    @Test
    void followRangeComesFromTheMobAttribute() {
        assertEquals(16, AggressiveGoal.followRangeOf(16.0));
        assertEquals(48, AggressiveGoal.followRangeOf(48.0));
        assertEquals(AggressiveGoal.DEFAULT_FOLLOW_RANGE, AggressiveGoal.followRangeOf(null));
    }
}
