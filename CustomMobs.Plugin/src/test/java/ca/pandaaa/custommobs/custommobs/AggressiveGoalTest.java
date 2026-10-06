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
        for (int i = 0; i < times; i++) {
            scene.ticksLived++;
            goal.tick();
        }
    }

    /** One server tick: the mob ages, Paper asks shouldActivate and runs the goal only if it says yes. */
    private boolean serverTick(AggressiveGoal goal) {
        scene.ticksLived++;
        if (!goal.shouldActivate())
            return false;
        goal.tick();
        return true;
    }

    /** Vetoes {@code p} once (goal.tick at the current age), then lets Origini accept it again. */
    private AggressiveGoal vetoOnce(Player p) {
        scene.vetoed.add(p);
        AggressiveGoal goal = goal(32, TargetPolicies.ALLOW_ALL);
        goal.tick();
        assertEquals(1, scene.targetEvents.size());
        assertTrue(scene.targetEvents.get(0).isCancelled());
        return goal;
    }

    @Test
    void withoutPolicyChasesAndHitsTheNearestPlayer() {
        Player near = scene.player("near", 1);
        scene.player("far", 10);
        tick(goal(32, TargetPolicies.ALLOW_ALL), 1);
        assertEquals(List.of(near), scene.attacked);
    }

    @Test
    void targetIsAskedToOtherPluginsWithACustomEvent() {
        Player p = scene.player("p", 1);
        tick(goal(32, TargetPolicies.ALLOW_ALL), 1);
        assertEquals(1, scene.targetEvents.size());
        assertSame(scene.mob, scene.targetEvents.get(0).getEntity());
        assertSame(p, scene.targetEvents.get(0).getTarget());
        assertEquals(org.bukkit.event.entity.EntityTargetEvent.TargetReason.CUSTOM, scene.targetEvents.get(0).getReason());
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
        assertEquals(1, scene.targetEvents.size(), "target asked " + scene.targetEvents.size() + " times");
        assertTrue(scene.setTargetCalls.isEmpty());
    }

    @Test
    void vetoIsStillValidBeforeItExpires() {
        Player p = scene.player("p", 1);
        AggressiveGoal goal = vetoOnce(p);
        scene.vetoed.clear(); // Origini would now accept, but the veto has not expired yet
        for (int i = 1; i < AggressiveGoal.VETO_RETRY_TICKS; i++)
            assertFalse(serverTick(goal), "asked again after " + i + " ticks");
        assertEquals(1, scene.targetEvents.size());
        assertTrue(scene.attacked.isEmpty());
    }

    @Test
    void vetoExpiresWhileTheGoalIsInactive() {
        Player p = scene.player("p", 1);
        AggressiveGoal goal = vetoOnce(p); // the goal stops: Paper then only calls shouldActivate every tick
        scene.vetoed.clear(); // e.g. origin changed, Origini no longer refuses
        boolean asksAgain = false;
        for (int i = 0; i < AggressiveGoal.VETO_RETRY_TICKS && !asksAgain; i++)
            asksAgain = serverTick(goal);
        assertTrue(asksAgain, "a vetoed player must be asked again after " + AggressiveGoal.VETO_RETRY_TICKS + " ticks");
    }

    @Test
    void afterExpiryThePlayerIsAskedAgainAndTaken() {
        Player p = scene.player("p", 1);
        AggressiveGoal goal = vetoOnce(p);
        scene.vetoed.clear();
        for (int i = 0; i < AggressiveGoal.VETO_RETRY_TICKS; i++)
            serverTick(goal);
        assertEquals(2, scene.targetEvents.size());
        assertFalse(scene.targetEvents.get(1).isCancelled());
        assertSame(p, scene.target);
        assertEquals(List.of(p), scene.attacked);
    }

    @Test
    void aNewVetoAtExpiryLastsAnotherFullPeriod() {
        Player p = scene.player("p", 1);
        AggressiveGoal goal = vetoOnce(p); // still refused at expiry
        for (int i = 0; i < AggressiveGoal.VETO_RETRY_TICKS; i++)
            serverTick(goal);
        assertEquals(2, scene.targetEvents.size(), "asked once more at expiry");
        for (int i = 1; i < AggressiveGoal.VETO_RETRY_TICKS; i++)
            serverTick(goal);
        assertEquals(2, scene.targetEvents.size(), "the renewed veto must last " + AggressiveGoal.VETO_RETRY_TICKS + " ticks");
        serverTick(goal);
        assertEquals(3, scene.targetEvents.size());
        assertTrue(scene.attacked.isEmpty());
    }

    @Test
    void vetoesArePerPlayer() {
        Player first = scene.player("first", 1);
        AggressiveGoal goal = vetoOnce(first);
        for (int i = 0; i < 5; i++)
            serverTick(goal);
        Player second = scene.player("second", 2);
        serverTick(goal);
        assertSame(second, scene.target, "the first player's veto must not block another player");
        assertEquals(List.of(first, second), scene.targetEvents.stream().map(e -> e.getTarget()).toList());
        assertFalse(scene.attacked.contains(first));
    }

    @Test
    void aRemovedMobLeavesNoVetoBehind() {
        Player p = scene.player("p", 1);
        vetoOnce(p);
        scene.vetoed.clear();
        // Goals are not saved with the entity: a respawned or reloaded mob gets a new goal (AggressiveGoal.apply).
        AggressiveGoal fresh = goal(32, TargetPolicies.ALLOW_ALL);
        assertTrue(serverTick(fresh), "a new goal starts without old vetoes");
        assertSame(p, scene.target);
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
