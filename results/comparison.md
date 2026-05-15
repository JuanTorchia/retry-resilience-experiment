# Retry Lab Comparison

Generated from local simulation data in `results/raw`. Do not treat these numbers as production evidence.

| run | total | success rate | error rate | success rps | attempt p95/p99 ms | success p95/p99 ms | downstream calls | amplification | retry attempts/req | timeouts | CB rejected | bulkhead rejected | max inflight |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 01-no-retry-low-timeout-fixed-delay | 10315 | 0.0005 | 0.9995 | 0.08 | 180/184 | 278/278 | 10315 | 1 | 0 | 10310 | 0 | 0 | 40 |
| 02-no-retry-standard-timeout-fixed-delay | 8840 | 1 | 0 | 140.26 | 220/222 | 220/222 | 8840 | 1 | 0 | 0 | 0 | 0 | 40 |
| 03-immediate-retry-fixed-delay | 8840 | 1 | 0 | 134.1 | 221/224 | 221/224 | 8840 | 1 | 0 | 0 | 0 | 0 | 40 |
| 04-exponential-backoff-fixed-delay | 8840 | 1 | 0 | 138.35 | 220/225 | 220/225 | 8840 | 1 | 0 | 0 | 0 | 0 | 40 |
| 05-jitter-fixed-delay | 8840 | 1 | 0 | 141.39 | 220/221 | 220/221 | 8840 | 1 | 0 | 0 | 0 | 0 | 40 |
| 06-no-retry-standard-timeout-random-failures | 13960 | 0.6529 | 0.3471 | 146.88 | 120/122 | 120/122 | 13960 | 1 | 0 | 0 | 0 | 0 | 40 |
| 07-immediate-retry-random-failures | 10557 | 0.955 | 0.045 | 161.17 | 120/121 | 360/360 | 15463 | 1.465 | 0.465 | 0 | 0 | 0 | 40 |
| 08-exponential-backoff-random-failures | 8860 | 0.9568 | 0.0432 | 135.71 | 120/120 | 586/586 | 12976 | 1.465 | 0.465 | 0 | 0 | 0 | 40 |
| 09-jitter-random-failures | 8814 | 0.957 | 0.043 | 134.02 | 120/120 | 573/648 | 12968 | 1.471 | 0.471 | 0 | 0 | 0 | 40 |
| 10-no-retry-standard-timeout-progressive-degradation | 7720 | 0.0076 | 0.9924 | 0.95 | 260/260 | 251/257 | 7720 | 1 | 0 | 7661 | 0 | 0 | 40 |
| 11-immediate-retry-progressive-degradation | 2939 | 0.0201 | 0.9799 | 0.95 | 260/260 | 251/257 | 8699 | 2.96 | 1.96 | 8640 | 0 | 0 | 43 |
| 12-jitter-progressive-degradation | 2337 | 0.0252 | 0.9748 | 0.94 | 260/260 | 251/257 | 6893 | 2.95 | 1.95 | 6834 | 0 | 0 | 40 |
| 13-circuit-breaker-progressive-degradation | 44777 | 0.0013 | 0.9987 | 0.94 | 0/2 | 259/264 | 198 | 0.004 | 0.003 | 139 | 44718 | 0 | 40 |
| 14-bulkhead-progressive-degradation | 22320 | 0.0026 | 0.9974 | 0.92 | 260/260 | 251/257 | 3668 | 0.164 | 0.155 | 3609 | 0 | 22122 | 16 |
| 15-no-retry-standard-timeout-latency-tail-spike | 14095 | 0.8333 | 0.1667 | 187.28 | 260/260 | 90/93 | 14095 | 1 | 0 | 2349 | 0 | 0 | 40 |
| 16-immediate-retry-latency-tail-spike | 12422 | 0.9972 | 0.0028 | 193.98 | 260/260 | 350/610 | 14864 | 1.197 | 0.197 | 2477 | 0 | 0 | 40 |
| 17-jitter-latency-tail-spike | 11474 | 0.9957 | 0.0043 | 177.58 | 260/260 | 446/846 | 13709 | 1.195 | 0.195 | 2284 | 0 | 0 | 40 |

Interpretation notes belong in `docs/brief-post.md`; this file is the mechanical comparison table.
