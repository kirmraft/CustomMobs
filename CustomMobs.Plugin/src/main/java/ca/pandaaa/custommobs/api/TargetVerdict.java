package ca.pandaaa.custommobs.api;

/**
 * A policy's answer about one candidate.
 *
 * @param allowed false: never target this player
 * @param weight  preference; 1 is neutral. A weight w is preferred over a weight-1 candidate up to w times farther
 *                away (score = distance / weight). Must be positive when allowed.
 */
public record TargetVerdict(boolean allowed, double weight) {
    public static final TargetVerdict ALLOW = new TargetVerdict(true, 1.0);
    public static final TargetVerdict DENY = new TargetVerdict(false, 0.0);

    public TargetVerdict {
        if (allowed && !(weight > 0))
            throw new IllegalArgumentException("an allowed target needs a positive weight, got " + weight);
    }

    public static TargetVerdict prefer(double weight) {
        return new TargetVerdict(true, weight);
    }
}
