package dev.juanchi.retrylab.core;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

@Component
public class MetricsRecorder {
    private final AtomicLong totalRequests = new AtomicLong();
    private final AtomicLong successfulRequests = new AtomicLong();
    private final AtomicLong downstreamCalls = new AtomicLong();
    private final AtomicInteger currentInflightDownstream = new AtomicInteger();
    private final AtomicInteger maxInflightDownstream = new AtomicInteger();
    private final List<Long> allAttemptLatenciesMs = Collections.synchronizedList(new ArrayList<>());
    private final List<Long> successfulRequestLatenciesMs = Collections.synchronizedList(new ArrayList<>());
    private volatile Instant startedAt = Instant.now();

    public void record(RequestOutcome outcome) {
        totalRequests.incrementAndGet();
        if (outcome.successful()) {
            successfulRequests.incrementAndGet();
            successfulRequestLatenciesMs.add(outcome.totalLatency().toMillis());
        }
        downstreamCalls.addAndGet(outcome.downstreamCalls());
        for (long attemptLatency : outcome.attemptLatenciesMs()) {
            allAttemptLatenciesMs.add(attemptLatency);
        }
    }

    public void onDownstreamCallStarted() {
        int inflight = currentInflightDownstream.incrementAndGet();
        maxInflightDownstream.accumulateAndGet(inflight, Math::max);
    }

    public void onDownstreamCallFinished() {
        currentInflightDownstream.decrementAndGet();
    }

    public MetricSnapshot snapshot() {
        long total = totalRequests.get();
        long successful = successfulRequests.get();
        long failed = total - successful;
        long downstream = downstreamCalls.get();
        double elapsedSeconds = Math.max(1.0, Duration.between(startedAt, Instant.now()).toMillis() / 1000.0);
        int maxInflight = maxInflightDownstream.get();
        return new MetricSnapshot(
                total,
                successful,
                failed,
                total == 0 ? 0.0 : failed / (double) total,
                successful / elapsedSeconds,
                percentile(allAttemptLatenciesMs, 0.95),
                percentile(allAttemptLatenciesMs, 0.99),
                percentile(successfulRequestLatenciesMs, 0.95),
                percentile(successfulRequestLatenciesMs, 0.99),
                downstream,
                total == 0 ? 0.0 : downstream / (double) total,
                currentInflightDownstream.get(),
                maxInflight,
                saturationObservation(maxInflight)
        );
    }

    public void reset() {
        totalRequests.set(0);
        successfulRequests.set(0);
        downstreamCalls.set(0);
        currentInflightDownstream.set(0);
        maxInflightDownstream.set(0);
        allAttemptLatenciesMs.clear();
        successfulRequestLatenciesMs.clear();
        startedAt = Instant.now();
    }

    private static long percentile(List<Long> values, double percentile) {
        List<Long> copy;
        synchronized (values) {
            copy = new ArrayList<>(values);
        }
        if (copy.isEmpty()) {
            return 0;
        }
        Collections.sort(copy);
        int index = (int) Math.ceil(percentile * copy.size()) - 1;
        return copy.get(Math.max(0, Math.min(index, copy.size() - 1)));
    }

    private static String saturationObservation(int maxInflight) {
        if (maxInflight >= 80) {
            return "high downstream concurrency; likely saturation or queueing under this workload";
        }
        if (maxInflight >= 30) {
            return "moderate downstream concurrency; watch for queueing if latency rises";
        }
        return "no visible downstream saturation in this run";
    }
}
