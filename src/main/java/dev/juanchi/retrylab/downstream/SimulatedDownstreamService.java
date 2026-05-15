package dev.juanchi.retrylab.downstream;

import dev.juanchi.retrylab.core.MetricsRecorder;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class SimulatedDownstreamService {
    private final MetricsRecorder metricsRecorder;
    private final AtomicLong callSequence = new AtomicLong();

    public SimulatedDownstreamService(MetricsRecorder metricsRecorder) {
        this.metricsRecorder = metricsRecorder;
    }

    public String call(DownstreamScenario scenario) {
        long callNumber = callSequence.incrementAndGet();
        metricsRecorder.onDownstreamCallStarted();
        try {
            sleep(scenario.delayForCall(callNumber));
            if (scenario == DownstreamScenario.RANDOM_FAILURES && ThreadLocalRandom.current().nextDouble() < 0.35) {
                throw new DownstreamException("simulated random downstream failure");
            }
            return "ok";
        } finally {
            metricsRecorder.onDownstreamCallFinished();
        }
    }

    public void reset() {
        callSequence.set(0);
    }

    private static void sleep(Duration duration) {
        try {
            Thread.sleep(duration.toMillis());
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new DownstreamException("downstream call interrupted");
        }
    }
}
