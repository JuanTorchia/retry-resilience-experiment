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
    private final AtomicLong retryAttempts = new AtomicLong();
    private final AtomicLong timeoutCount = new AtomicLong();
    private final AtomicLong circuitBreakerRejected = new AtomicLong();
    private final AtomicLong bulkheadRejected = new AtomicLong();
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
        retryAttempts.addAndGet(Math.max(0, outcome.attempts() - 1));
        timeoutCount.addAndGet(outcome.timeoutCount());
        circuitBreakerRejected.addAndGet(outcome.circuitBreakerRejected());
        bulkheadRejected.addAndGet(outcome.bulkheadRejected());
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
                total == 0 ? 0.0 : successful / (double) total,
                total == 0 ? 0.0 : failed / (double) total,
                successful / elapsedSeconds,
                percentile(allAttemptLatenciesMs, 0.95),
                percentile(allAttemptLatenciesMs, 0.99),
                percentile(successfulRequestLatenciesMs, 0.95),
                percentile(successfulRequestLatenciesMs, 0.99),
                downstream,
                total == 0 ? 0.0 : downstream / (double) total,
                retryAttempts.get(),
                total == 0 ? 0.0 : retryAttempts.get() / (double) total,
                timeoutCount.get(),
                circuitBreakerRejected.get(),
                bulkheadRejected.get(),
                currentInflightDownstream.get(),
                maxInflight,
                concurrencyObservation(maxInflight)
        );
    }

    public void reset() {
        totalRequests.set(0);
        successfulRequests.set(0);
        downstreamCalls.set(0);
        retryAttempts.set(0);
        timeoutCount.set(0);
        circuitBreakerRejected.set(0);
        bulkheadRejected.set(0);
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

    private static String concurrencyObservation(int maxInflight) {
        if (maxInflight >= 80) {
            return "high observed downstream concurrency; inspect queueing and resource saturation separately";
        }
        if (maxInflight >= 30) {
            return "moderate observed downstream concurrency; not proof of saturation by itself";
        }
        return "low observed downstream concurrency in this run";
    }
}
