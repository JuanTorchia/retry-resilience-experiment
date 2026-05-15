package dev.juanchi.retrylab.downstream;

import java.time.Duration;
import java.util.Locale;

public enum DownstreamScenario {
    FIXED_DELAY,
    RANDOM_FAILURES,
    PROGRESSIVE_DEGRADATION;

    public static DownstreamScenario fromName(String rawName) {
        String normalized = rawName == null ? "" : rawName.toLowerCase(Locale.ROOT).replace('-', '_');
        return switch (normalized) {
            case "random_failures" -> RANDOM_FAILURES;
            case "progressive_degradation" -> PROGRESSIVE_DEGRADATION;
            default -> FIXED_DELAY;
        };
    }

    public Duration delayForCall(long callNumber) {
        return switch (this) {
            case FIXED_DELAY -> Duration.ofMillis(220);
            case RANDOM_FAILURES -> Duration.ofMillis(120);
            case PROGRESSIVE_DEGRADATION -> Duration.ofMillis(Math.min(900, 80 + callNumber * 3));
        };
    }
}
