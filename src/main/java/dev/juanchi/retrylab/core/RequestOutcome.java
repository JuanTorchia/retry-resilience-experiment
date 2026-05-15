package dev.juanchi.retrylab.core;

import java.time.Duration;

public record RequestOutcome(
        boolean successful,
        int attempts,
        long downstreamCalls,
        long timeoutCount,
        long circuitBreakerRejected,
        long bulkheadRejected,
        Duration totalLatency,
        long[] attemptLatenciesMs,
        String terminalStatus
) {
}
