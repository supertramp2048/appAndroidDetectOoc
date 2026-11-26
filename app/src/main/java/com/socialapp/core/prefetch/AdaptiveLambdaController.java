package com.socialapp.core.prefetch;

/** Stateless feedback rule. An observation contains only outcomes available at its boundary. */
public final class AdaptiveLambdaController {
    public enum Reason {
        WAITING_FOR_OBSERVATION, NO_RESOLVED_PREFETCH, NO_SUCCESSFUL_LOADS, INSUFFICIENT_SAMPLES,
        HIGH_WASTE, LOW_WASTE_HIGH_MISS, STABLE, AT_MAXIMUM, AT_MINIMUM, FIXED, LEGACY
    }

    public static final class Config {
        public static final Config DEFAULT = new Config(0.000075, 0.000015, 0.004, 8, 3);

        public final double initial;
        public final double min;
        public final double max;
        public final int loadsPerObservation;
        public final int minimumSamples;

        public Config(double initial, double min, double max, int loadsPerObservation, int minimumSamples) {
            if (!Double.isFinite(min) || min <= 0 || !Double.isFinite(initial) || !Double.isFinite(max)
                    || initial < min || initial > max || loadsPerObservation < 1 || minimumSamples < 1) {
                throw new IllegalArgumentException("Invalid lambda configuration");
            }
            this.initial = initial;
            this.min = min;
            this.max = max;
            this.loadsPerObservation = loadsPerObservation;
            this.minimumSamples = minimumSamples;
        }
    }

    public static final double WASTE_TARGET = 0.30;
    private static final double WASTE_LOWER = 0.25;
    private static final double WASTE_UPPER = 0.35;
    private static final double MISS_TARGET = 0.50;

    static final class Decision {
        final double lambda;
        final Reason reason;

        Decision(double lambda, Reason reason) {
            this.lambda = lambda;
            this.reason = reason;
        }
    }

    private final Config config;

    public AdaptiveLambdaController(Config config) {
        this.config = config;
    }

    Decision evaluate(double lambda, long resolvedBytes, long wastedBytes, int resolvedCount,
                      int successfulLoads, int misses) {
        if (resolvedBytes == 0) return new Decision(lambda, Reason.NO_RESOLVED_PREFETCH);
        if (successfulLoads == 0) return new Decision(lambda, Reason.NO_SUCCESSFUL_LOADS);
        if (resolvedCount < config.minimumSamples || successfulLoads < config.minimumSamples) {
            return new Decision(lambda, Reason.INSUFFICIENT_SAMPLES);
        }
        double waste = (double) wastedBytes / resolvedBytes;
        double miss = (double) misses / successfulLoads;
        if (waste > WASTE_UPPER) {
            double next = Math.min(config.max, lambda * 1.2);
            return new Decision(next, next > lambda ? Reason.HIGH_WASTE : Reason.AT_MAXIMUM);
        }
        if (waste < WASTE_LOWER && miss > MISS_TARGET) {
            double next = Math.max(config.min, lambda * 0.8);
            return new Decision(next, next < lambda ? Reason.LOW_WASTE_HIGH_MISS : Reason.AT_MINIMUM);
        }
        return new Decision(lambda, Reason.STABLE);
    }
}
