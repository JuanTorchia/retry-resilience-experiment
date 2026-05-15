package dev.juanchi.retrylab.core;

public record MetricSnapshot(
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        double successRate,
        double errorRate,
        double successfulRequestsPerSecond,
        long allAttemptLatencyP95Ms,
        long allAttemptLatencyP99Ms,
        long successLatencyP95Ms,
        long successLatencyP99Ms,
        long downstreamCalls,
        double retryAmplificationFactor,
        long retryAttempts,
        double retryAttemptsPerRequest,
        long timeoutCount,
        long circuitBreakerRejected,
        long bulkheadRejected,
        int currentInflightDownstream,
        int maxInflightDownstream,
        String saturationObservation
) {
}
