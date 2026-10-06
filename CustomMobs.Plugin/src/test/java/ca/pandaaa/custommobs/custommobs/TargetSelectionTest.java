package ca.pandaaa.custommobs.custommobs;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import ca.pandaaa.custommobs.api.TargetVerdict;

class TargetSelectionTest {
    private final Map<String, Double> distance = new HashMap<>();
    private final Map<String, TargetVerdict> verdicts = new HashMap<>();
    private final Set<String> vetoed = new HashSet<>();
    private final Set<String> unreachable = new HashSet<>();

    private String choose(String current, boolean reconsider, String... candidates) {
        distance.keySet().forEach(k -> verdicts.putIfAbsent(k, TargetVerdict.ALLOW));
        return TargetSelection.choose(current, List.of(candidates), c -> distance.get(c) * distance.get(c), 32,
                verdicts::get, vetoed::contains, c -> !unreachable.contains(c), reconsider);
    }

    private void at(String name, double blocks) {
        distance.put(name, blocks);
    }

    @Test
    void withoutPolicyKeepsCurrentElseTakesNearest() {
        at("a", 10);
        at("b", 5);
        assertEquals("a", choose("a", false, "a", "b"));
        assertEquals("b", choose(null, false, "a", "b"));
    }

    @Test
    void deniedIsNeverChosen() {
        at("a", 5);
        at("b", 20);
        verdicts.put("a", TargetVerdict.DENY);
        assertEquals("b", choose(null, true, "a", "b"));
    }

    @Test
    void deniedCurrentTargetIsDropped() {
        at("a", 5);
        at("b", 20);
        verdicts.put("a", TargetVerdict.DENY);
        assertEquals("b", choose("a", true, "a", "b"));
    }

    @Test
    void vetoedIsNeverChosenNotEvenAsCurrent() {
        at("a", 5);
        at("b", 20);
        vetoed.add("a");
        assertEquals("b", choose("a", false, "a", "b"));
        assertNull(choose(null, false, "a"));
    }

    @Test
    void priorityWinsWithinItsWeight() {
        at("near", 10);
        at("prio", 13);
        verdicts.put("prio", TargetVerdict.prefer(1.4));
        assertEquals("prio", choose(null, true, "near", "prio"));
    }

    @Test
    void priorityLosesBeyondItsWeight() {
        at("near", 10);
        at("prio", 15);
        verdicts.put("prio", TargetVerdict.prefer(1.4));
        assertEquals("near", choose(null, true, "near", "prio"));
    }

    @Test
    void vetoBeatsPriority() {
        at("near", 10);
        at("prio", 11);
        verdicts.put("prio", TargetVerdict.prefer(5));
        vetoed.add("prio");
        assertEquals("near", choose(null, true, "near", "prio"));
    }

    @Test
    void reconsideringTakesTheBestScoreAndKeepsTheCurrentOnATie() {
        at("current", 10);
        at("closer", 5);
        assertEquals("closer", choose("current", true, "current", "closer"));
        at("closer", 10);
        assertEquals("current", choose("current", true, "closer", "current"));
    }

    @Test
    void scoreIsDistanceOverWeight() {
        at("a", 10); // score 10
        at("b", 13); // score 13 / 1.4 = 9.29
        verdicts.put("b", TargetVerdict.prefer(1.4));
        assertEquals("b", choose("a", true, "a", "b"));
        at("b", 15); // score 10.71
        assertEquals("a", choose("b", true, "a", "b"));
        at("b", 13);
        assertEquals("b", choose("a", true, "a", "b"));
    }

    @Test
    void denyBeatsAnyWeightEvenOnTheCurrentTarget() {
        at("a", 10);
        at("b", 13);
        verdicts.put("b", TargetVerdict.DENY);
        assertEquals("a", choose("b", true, "a", "b"));
        assertEquals("a", choose("b", false, "a", "b"));
    }

    @Test
    void deniedAAllowedB() {
        at("a", 10);
        at("b", 13);
        verdicts.put("a", TargetVerdict.DENY);
        assertEquals("b", choose(null, true, "a", "b"));
    }

    @Test
    void everybodyDeniedMeansNoTarget() {
        at("a", 10);
        at("b", 13);
        verdicts.put("a", TargetVerdict.DENY);
        verdicts.put("b", TargetVerdict.DENY);
        assertNull(choose("b", true, "a", "b"));
        assertNull(choose("b", false, "a", "b"));
    }

    @Test
    void unreachablePreferredFallsBackToNearest() {
        at("near", 10);
        at("prio", 12);
        verdicts.put("prio", TargetVerdict.prefer(2));
        unreachable.add("prio");
        assertEquals("near", choose(null, true, "near", "prio"));
    }

    @Test
    void outOfFollowRangeIsIgnoredWhateverTheWeight() {
        at("far", 40);
        verdicts.put("far", TargetVerdict.prefer(10));
        assertNull(choose(null, true, "far"));
        assertNull(choose("far", false, "far"));
    }
}
