package dev.juanchi.retrylab.api;

import dev.juanchi.retrylab.core.*;
import dev.juanchi.retrylab.downstream.DownstreamScenario;
import dev.juanchi.retrylab.downstream.SimulatedDownstreamService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api")
public class LabController {
    private final RetryExecutor retryExecutor;
    private final MetricsRecorder metricsRecorder;
    private final SimulatedDownstreamService downstreamService;

    public LabController(
            RetryExecutor retryExecutor,
            MetricsRecorder metricsRecorder,
            SimulatedDownstreamService downstreamService
    ) {
        this.retryExecutor = retryExecutor;
        this.metricsRecorder = metricsRecorder;
        this.downstreamService = downstreamService;
    }

    @GetMapping("/work")
    public ResponseEntity<Map<String, Object>> work(
            @RequestParam(name = "policy", defaultValue = "no-retry-low-timeout") String policy,
            @RequestParam(name = "scenario", defaultValue = "fixed-delay") String scenario
    ) {
        RetryPolicy retryPolicy = RetryPolicy.fromName(policy);
        DownstreamScenario downstreamScenario = DownstreamScenario.fromName(scenario);
        RequestOutcome outcome = retryExecutor.execute(retryPolicy, downstreamScenario);
        metricsRecorder.record(outcome);

        ResponseEntity.BodyBuilder response = outcome.successful()
                ? ResponseEntity.ok()
                : ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE);

        return response
                .header("X-Attempts", Integer.toString(outcome.attempts()))
                .header("X-Downstream-Calls", Long.toString(outcome.downstreamCalls()))
                .header("X-Timeout-Count", Long.toString(outcome.timeoutCount()))
                .header("X-Circuit-Breaker-Rejected", Long.toString(outcome.circuitBreakerRejected()))
                .header("X-Bulkhead-Rejected", Long.toString(outcome.bulkheadRejected()))
                .header("X-Attempt-Latencies-Ms", Arrays.stream(outcome.attemptLatenciesMs())
                        .mapToObj(Long::toString)
                        .collect(Collectors.joining(",")))
                .body(Map.of(
                        "successful", outcome.successful(),
                        "policy", retryPolicy.name(),
                        "scenario", downstreamScenario.name().toLowerCase(),
                        "attempts", outcome.attempts(),
                        "downstreamCalls", outcome.downstreamCalls(),
                        "timeoutCount", outcome.timeoutCount(),
                        "circuitBreakerRejected", outcome.circuitBreakerRejected(),
                        "bulkheadRejected", outcome.bulkheadRejected(),
                        "terminalStatus", outcome.terminalStatus()
                ));
    }

    @GetMapping("/metrics")
    public MetricSnapshot metrics() {
        return metricsRecorder.snapshot();
    }

    @PostMapping("/reset")
    public MetricSnapshot reset() {
        metricsRecorder.reset();
        downstreamService.reset();
        retryExecutor.resetGuards();
        return metricsRecorder.snapshot();
    }
}
