package dev.juanchi.retrylab.downstream;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class DownstreamScenarioTest {

    @Test
    void latencyTailSpikeMostlyReturnsFastButInjectsPredictableSlowTail() {
        DownstreamScenario scenario = DownstreamScenario.fromName("latency-tail-spike");

        assertThat(scenario).isEqualTo(DownstreamScenario.LATENCY_TAIL_SPIKE);
        assertThat(scenario.delayForCall(1)).isEqualTo(Duration.ofMillis(90));
        assertThat(scenario.delayForCall(6)).isEqualTo(Duration.ofMillis(650));
        assertThat(scenario.delayForCall(12)).isEqualTo(Duration.ofMillis(650));
    }
}
