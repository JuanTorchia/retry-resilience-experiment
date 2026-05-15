package dev.juanchi.retrylab.core;

public record MetricSnapshot(
        long totalRequests,
        long successfulRequests,
        long failedRequests,
        double errorRate,
        double successfulRequestsPerSecond,
        long allAttemptLatencyP95Ms,
        long allAttemptLatencyP99Ms,
        long successLatencyP95Ms,
        long successLatencyP99Ms,
        long downstreamCalls,
        double retryAmplificationFactor,
        int currentInflightDownstream,
        int maxInflightDownstream,
        String saturationObservation
) {
}
