import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const baseUrl = __ENV.BASE_URL || 'http://localhost:18080';
const policy = __ENV.POLICY || 'no-retry-standard-timeout';
const scenario = __ENV.SCENARIO || 'fixed-delay';

export const options = {
  vus: Number(__ENV.K6_VUS || 40),
  duration: __ENV.K6_DURATION || '30s',
  thresholds: {},
};

const successfulRequests = new Counter('successful_requests');
const failedRequests = new Counter('failed_requests');
const downstreamCalls = new Counter('downstream_calls');
const timeoutCount = new Counter('timeout_count');
const circuitBreakerRejected = new Counter('circuit_breaker_rejected');
const bulkheadRejected = new Counter('bulkhead_rejected');
const retryAmplificationSamples = new Trend('retry_amplification_factor');
const retryAttemptsPerRequest = new Trend('retry_attempts_per_request');
const successfulRequestLatency = new Trend('successful_request_latency');
const allAttemptLatency = new Trend('all_attempt_latency');
const requestFailed = new Rate('request_failed');

export default function () {
  const response = http.get(`${baseUrl}/api/work?policy=${policy}&scenario=${scenario}`, {
    timeout: '5s',
    tags: { policy, scenario },
  });

  const ok = response.status === 200;
  check(response, { 'request succeeded': () => ok });
  requestFailed.add(!ok);

  const attempts = Number(response.headers['X-Attempts'] || '0');
  const realDownstreamCalls = Number(response.headers['X-Downstream-Calls'] || attempts || '0');
  const timeouts = Number(response.headers['X-Timeout-Count'] || '0');
  const circuitRejected = Number(response.headers['X-Circuit-Breaker-Rejected'] || '0');
  const bulkheadFull = Number(response.headers['X-Bulkhead-Rejected'] || '0');
  downstreamCalls.add(realDownstreamCalls);
  retryAmplificationSamples.add(realDownstreamCalls);
  retryAttemptsPerRequest.add(Math.max(0, attempts - 1));
  timeoutCount.add(timeouts);
  circuitBreakerRejected.add(circuitRejected);
  bulkheadRejected.add(bulkheadFull);

  const latencies = response.headers['X-Attempt-Latencies-Ms'] || '';
  for (const latency of latencies.split(',')) {
    if (latency !== '') {
      allAttemptLatency.add(Number(latency));
    }
  }

  if (ok) {
    successfulRequests.add(1);
    successfulRequestLatency.add(response.timings.duration);
  } else {
    failedRequests.add(1);
  }

  sleep(0.05);
}
