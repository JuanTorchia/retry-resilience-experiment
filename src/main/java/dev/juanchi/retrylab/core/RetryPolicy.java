package dev.juanchi.retrylab.core;

import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;

public record RetryPolicy(
        String name,
        int maxAttempts,
        Duration timeout,
        BackoffMode backoffMode,
        boolean circuitBreaker,
        boolean bulkhead
) {
    private static final Duration LOW_TIMEOUT = Duration.ofMillis(180);
    private static final Duration STANDARD_TIMEOUT = Duration.ofMillis(260);
    private static final Duration BASE_BACKOFF = Duration.ofMillis(75);

    public static RetryPolicy fromName(String rawName) {
        String normalized = rawName == null ? "" : rawName.toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case "no-retry-standard-timeout" -> new RetryPolicy(normalized, 1, STANDARD_TIMEOUT, BackoffMode.NONE, false, false);
            case "immediate-retry" -> new RetryPolicy(normalized, 3, STANDARD_TIMEOUT, BackoffMode.NONE, false, false);
            case "exponential-backoff" -> new RetryPolicy(normalized, 3, STANDARD_TIMEOUT, BackoffMode.EXPONENTIAL, false, false);
            case "jitter" -> new RetryPolicy(normalized, 3, STANDARD_TIMEOUT, BackoffMode.JITTER, false, false);
            case "circuit-breaker" -> new RetryPolicy(normalized, 3, STANDARD_TIMEOUT, BackoffMode.JITTER, true, false);
            case "bulkhead" -> new RetryPolicy(normalized, 3, STANDARD_TIMEOUT, BackoffMode.JITTER, false, true);
            default -> new RetryPolicy("no-retry-low-timeout", 1, LOW_TIMEOUT, BackoffMode.NONE, false, false);
        };
    }

    public Duration delayBeforeAttempt(int attemptNumber) {
        if (attemptNumber <= 1 || backoffMode == BackoffMode.NONE) {
            return Duration.ZERO;
        }
        long exponentialMs = BASE_BACKOFF.toMillis() * (1L << Math.max(0, attemptNumber - 2));
        if (backoffMode == BackoffMode.EXPONENTIAL) {
            return Duration.ofMillis(exponentialMs);
        }
        long min = Math.max(1, exponentialMs / 2);
        long max = Math.max(min, exponentialMs + exponentialMs / 2);
        return Duration.ofMillis(ThreadLocalRandom.current().nextLong(min, max + 1));
    }
}
