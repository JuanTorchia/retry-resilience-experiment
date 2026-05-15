package dev.juanchi.retrylab.core;

import dev.juanchi.retrylab.downstream.DownstreamScenario;
import dev.juanchi.retrylab.downstream.SimulatedDownstreamService;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

@Service
public class RetryExecutor {
    private final SimulatedDownstreamService downstreamService;
    private final ExecutorService attemptExecutor = Executors.newFixedThreadPool(128);
    private final Semaphore bulkhead = new Semaphore(16);
    private final CircuitBreaker circuitBreaker;

    public RetryExecutor(SimulatedDownstreamService downstreamService) {
        this.downstreamService = downstreamService;
        CircuitBreakerConfig config = CircuitBreakerConfig.custom()
                .failureRateThreshold(50)
                .slidingWindowSize(20)
                .minimumNumberOfCalls(10)
                .waitDurationInOpenState(Duration.ofSeconds(2))
                .permittedNumberOfCallsInHalfOpenState(4)
                .build();
        this.circuitBreaker = CircuitBreaker.of("downstream", config);
    }

    public RequestOutcome execute(RetryPolicy policy, DownstreamScenario scenario) {
        long requestStarted = System.nanoTime();
        List<Long> attemptLatencies = new ArrayList<>();
        int attempts = 0;
        int downstreamCalls = 0;
        String terminalStatus = "unknown";

        for (int attempt = 1; attempt <= policy.maxAttempts(); attempt++) {
            delay(policy.delayBeforeAttempt(attempt));
            attempts++;

            AttemptResult result = executeAttempt(policy, scenario);
            attemptLatencies.add(result.latencyMs());
            terminalStatus = result.status();
            downstreamCalls += result.downstreamCallStarted() ? 1 : 0;
            if (result.successful()) {
                return outcome(true, attempts, downstreamCalls, requestStarted, attemptLatencies, terminalStatus);
            }
            if ("circuit-open".equals(result.status()) || "bulkhead-full".equals(result.status())) {
                break;
            }
        }

        return outcome(false, attempts, downstreamCalls, requestStarted, attemptLatencies, terminalStatus);
    }

    public void resetGuards() {
        circuitBreaker.reset();
    }

    private AttemptResult executeAttempt(RetryPolicy policy, DownstreamScenario scenario) {
        long started = System.nanoTime();
        Future<String> future = attemptExecutor.submit(() -> guardedCall(policy, scenario));
        try {
            future.get(policy.timeout().toMillis(), TimeUnit.MILLISECONDS);
            return new AttemptResult(true, elapsedMs(started), "ok", true);
        } catch (TimeoutException timeout) {
            future.cancel(true);
            return new AttemptResult(false, elapsedMs(started), "timeout", true);
        } catch (ExecutionException execution) {
            Throwable cause = execution.getCause();
            if (cause instanceof RejectedExecutionException) {
                return new AttemptResult(false, elapsedMs(started), "bulkhead-full", false);
            }
            if (cause instanceof CallNotPermittedException) {
                return new AttemptResult(false, elapsedMs(started), "circuit-open", false);
            }
            return new AttemptResult(false, elapsedMs(started), "downstream-error", true);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return new AttemptResult(false, elapsedMs(started), "interrupted", false);
        }
    }

    private String guardedCall(RetryPolicy policy, DownstreamScenario scenario) {
        Supplier<String> supplier = () -> downstreamService.call(scenario);
        if (policy.bulkhead()) {
            supplier = withBulkhead(supplier);
        }
        if (policy.circuitBreaker()) {
            supplier = CircuitBreaker.decorateSupplier(circuitBreaker, supplier);
        }
        return supplier.get();
    }

    private Supplier<String> withBulkhead(Supplier<String> supplier) {
        return () -> {
            if (!bulkhead.tryAcquire()) {
                throw new RejectedExecutionException("simulated bulkhead is full");
            }
            try {
                return supplier.get();
            } finally {
                bulkhead.release();
            }
        };
    }

    private static RequestOutcome outcome(
            boolean successful,
            int attempts,
            int downstreamCalls,
            long requestStarted,
            List<Long> attemptLatencies,
            String terminalStatus
    ) {
        long[] attemptArray = attemptLatencies.stream().mapToLong(Long::longValue).toArray();
        return new RequestOutcome(
                successful,
                attempts,
                downstreamCalls,
                Duration.ofMillis(elapsedMs(requestStarted)),
                attemptArray,
                terminalStatus
        );
    }

    private static void delay(Duration duration) {
        if (duration.isZero()) {
            return;
        }
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private static long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private record AttemptResult(boolean successful, long latencyMs, String status, boolean downstreamCallStarted) {
    }
}
