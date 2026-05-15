package dev.juanchi.retrylab.core;

import java.time.Duration;

public record RequestOutcome(
        boolean successful,
        int attempts,
        long downstreamCalls,
        Duration totalLatency,
        long[] attemptLatenciesMs,
        String terminalStatus
) {
}
