package ca.pandaaa.custommobs.custommobs;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

import ca.pandaaa.custommobs.api.TargetVerdict;

/**
 * Which player a CustomMob targets. Pure: the caller gathers candidates (same world, attackable game mode).
 *
 * <p>Order of the rules: follow range, then vetoes from other plugins, then the policy's DENY, then the score
 * {@code distance / weight} (weight 1.4 wins up to 1.4 times farther). A veto or DENY always beats a weight.
 * When reconsidering, every valid candidate is scored again and the current target is kept only if nobody scores
 * better. Without reconsidering (no policy) this is the old behaviour: keep a still-valid current target, otherwise
 * take the nearest.
 */
public final class TargetSelection {
    private TargetSelection() {
    }

    /**
     * @param current         the mob's current target, or null (also expected among the candidates when reconsidering)
     * @param candidates      players the mob could target
     * @param distanceSquared squared distance from the mob
     * @param followRange     how far the mob looks
     * @param verdict         the registered policy's answer
     * @param vetoed          players another plugin refused recently (EntityTargetEvent cancelled)
     * @param reachable       whether the mob can walk to a candidate; asked only for a farther, preferred one
     * @param reconsider      false keeps a still-valid current target without looking further (no policy registered)
     */
    public static <T> T choose(T current, Iterable<T> candidates, ToDoubleFunction<T> distanceSquared, double followRange,
            Function<T, TargetVerdict> verdict, Predicate<T> vetoed, Predicate<T> reachable, boolean reconsider) {
        double range = followRange * followRange;
        if (current != null && !reconsider && score(current, distanceSquared, range, verdict, vetoed, -1) != null)
            return current;

        List<Scored<T>> allowed = new ArrayList<>();
        int index = 0;
        for (T candidate : candidates) {
            Scored<T> scored = score(candidate, distanceSquared, range, verdict, vetoed, index++);
            if (scored != null)
                allowed.add(scored);
        }
        // Lowest score first; on a tie the current target stays, else the later candidate wins (old "distance <= best").
        allowed.sort(Comparator.<Scored<T>>comparingDouble(Scored::score)
                .thenComparing(s -> s.target() == current ? 0 : 1)
                .thenComparing(s -> -s.index()));
        double nearest = allowed.stream().mapToDouble(Scored::distance).min().orElse(Double.MAX_VALUE);

        for (Scored<T> s : allowed) {
            // A preferred player farther than the nearest one only wins if the mob can actually get there.
            if (s.distance() > nearest && !reachable.test(s.target()))
                continue;
            return s.target();
        }
        return null;
    }

    private static <T> Scored<T> score(T candidate, ToDoubleFunction<T> distanceSquared, double range,
            Function<T, TargetVerdict> verdict, Predicate<T> vetoed, int index) {
        double distance = distanceSquared.applyAsDouble(candidate);
        if (distance > range || vetoed.test(candidate))
            return null;
        TargetVerdict v = verdict.apply(candidate);
        if (v == null || !v.allowed())
            return null;
        return new Scored<>(candidate, distance, Math.sqrt(distance) / v.weight(), index);
    }

    /** {@code distance} is squared (cheap to compare); {@code score} is the real distance divided by the weight. */
    private record Scored<T>(T target, double distance, double score, int index) {
    }
}
