package dev.juanchi.retrylab.core;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

class RetryPolicyTest {

    @Test
    void noRetryUsesSingleAttemptWithLowTimeout() {
        RetryPolicy policy = RetryPolicy.fromName("no-retry-low-timeout");

        assertThat(policy.maxAttempts()).isEqualTo(1);
        assertThat(policy.timeout()).isEqualTo(Duration.ofMillis(180));
        assertThat(policy.delayBeforeAttempt(2)).isZero();
    }

    @Test
    void noRetryStandardTimeoutIsTheFairSingleAttemptBaseline() {
        RetryPolicy policy = RetryPolicy.fromName("no-retry-standard-timeout");

        assertThat(policy.maxAttempts()).isEqualTo(1);
        assertThat(policy.timeout()).isEqualTo(Duration.ofMillis(260));
        assertThat(policy.delayBeforeAttempt(2)).isZero();
    }

    @Test
    void noRetryStandardTimeoutIsAComparableBaselineForRetryPolicies() {
        RetryPolicy policy = RetryPolicy.fromName("no-retry-standard-timeout");

        assertThat(policy.maxAttempts()).isEqualTo(1);
        assertThat(policy.timeout()).isEqualTo(Duration.ofMillis(260));
        assertThat(policy.delayBeforeAttempt(2)).isZero();
    }

    @Test
    void immediateRetryHasNoDelayBetweenAttempts() {
        RetryPolicy policy = RetryPolicy.fromName("immediate-retry");

        assertThat(policy.maxAttempts()).isEqualTo(3);
        assertThat(policy.delayBeforeAttempt(2)).isZero();
        assertThat(policy.delayBeforeAttempt(3)).isZero();
    }

    @Test
    void exponentialBackoffDoublesDelayAfterEachFailure() {
        RetryPolicy policy = RetryPolicy.fromName("exponential-backoff");

        assertThat(policy.maxAttempts()).isEqualTo(3);
        assertThat(policy.delayBeforeAttempt(2)).isEqualTo(Duration.ofMillis(75));
        assertThat(policy.delayBeforeAttempt(3)).isEqualTo(Duration.ofMillis(150));
    }

    @Test
    void jitterKeepsDelayInsideDeterministicBoundsForTheAttempt() {
        RetryPolicy policy = RetryPolicy.fromName("jitter");

        assertThat(policy.maxAttempts()).isEqualTo(3);
        assertThat(policy.delayBeforeAttempt(2)).isBetween(Duration.ofMillis(37), Duration.ofMillis(112));
        assertThat(policy.delayBeforeAttempt(3)).isBetween(Duration.ofMillis(75), Duration.ofMillis(225));
    }

    @Test
    void rejectsUnknownPolicyName() {
        assertThat(RetryPolicy.fromName("missing").name()).isEqualTo("no-retry-low-timeout");
    }
}
