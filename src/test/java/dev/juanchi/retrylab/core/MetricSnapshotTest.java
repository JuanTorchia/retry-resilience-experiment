package dev.juanchi.retrylab.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class MetricSnapshotTest {

    @Test
    void computesCountersPercentilesAndRetryAmplification() {
        MetricsRecorder recorder = new MetricsRecorder();
        recorder.record(new RequestOutcome(true, 2, 2, Duration.ofMillis(120), new long[]{50, 70}, "ok"));
        recorder.record(new RequestOutcome(false, 3, 3, Duration.ofMillis(300), new long[]{90, 100, 110}, "timeout"));

        MetricSnapshot snapshot = recorder.snapshot();

        assertThat(snapshot.totalRequests()).isEqualTo(2);
        assertThat(snapshot.successfulRequests()).isEqualTo(1);
        assertThat(snapshot.failedRequests()).isEqualTo(1);
        assertThat(snapshot.errorRate()).isEqualTo(0.5);
        assertThat(snapshot.downstreamCalls()).isEqualTo(5);
        assertThat(snapshot.retryAmplificationFactor()).isEqualTo(2.5);
        assertThat(snapshot.allAttemptLatencyP95Ms()).isEqualTo(110);
        assertThat(snapshot.successLatencyP95Ms()).isEqualTo(120);
    }

    @Test
    void emptySnapshotUsesZeroes() {
        MetricSnapshot snapshot = new MetricsRecorder().snapshot();

        assertThat(snapshot.totalRequests()).isZero();
        assertThat(snapshot.errorRate()).isZero();
        assertThat(snapshot.retryAmplificationFactor()).isZero();
        assertThat(snapshot.allAttemptLatencyP99Ms()).isZero();
    }
}
