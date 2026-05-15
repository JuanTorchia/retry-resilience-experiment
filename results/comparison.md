# Retry Lab Comparison

Generated from local simulation data in `results/raw`. Do not treat these numbers as production evidence.

| run | total | success | failed | error rate | success rps | attempt p95/p99 ms | success p95/p99 ms | downstream calls | amplification | max inflight | observation |
|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|
| 01-no-retry-low-timeout-fixed-delay | 4865 | 49 | 4816 | 0.9899 | 1.48 | 200/249 | 325/354 | 4865 | 1 | 40 | moderate downstream concurrency; watch for queueing if latency rises |
| 02-no-retry-standard-timeout-fixed-delay | 4400 | 4400 | 0 | 0 | 142.66 | 223/231 | 223/231 | 4400 | 1 | 40 | moderate downstream concurrency; watch for queueing if latency rises |
| 03-immediate-retry-fixed-delay | 4422 | 4422 | 0 | 0 | 141.89 | 221/225 | 221/225 | 4422 | 1 | 40 | moderate downstream concurrency; watch for queueing if latency rises |
| 04-exponential-backoff-fixed-delay | 4440 | 4440 | 0 | 0 | 143.43 | 221/222 | 221/222 | 4440 | 1 | 40 | moderate downstream concurrency; watch for queueing if latency rises |
| 05-jitter-fixed-delay | 4440 | 4440 | 0 | 0 | 140.93 | 221/224 | 221/224 | 4440 | 1 | 40 | moderate downstream concurrency; watch for queueing if latency rises |
| 06-circuit-breaker-fixed-delay | 4440 | 4440 | 0 | 0 | 143.56 | 221/226 | 221/226 | 4440 | 1 | 40 | moderate downstream concurrency; watch for queueing if latency rises |
| 07-bulkhead-fixed-delay | 14108 | 2114 | 11994 | 0.8502 | 67.62 | 220/220 | 221/224 | 2114 | 0.15 | 16 | no visible downstream saturation in this run |
| 08-no-retry-standard-timeout-random-failures | 6986 | 4554 | 2432 | 0.3481 | 146.99 | 120/124 | 120/124 | 6986 | 1 | 40 | moderate downstream concurrency; watch for queueing if latency rises |
| 09-immediate-retry-random-failures | 5262 | 5060 | 202 | 0.0384 | 162.71 | 120/122 | 360/361 | 7761 | 1.475 | 40 | moderate downstream concurrency; watch for queueing if latency rises |
| 10-jitter-random-failures | 4484 | 4308 | 176 | 0.0393 | 137.68 | 120/120 | 563/643 | 6526 | 1.455 | 40 | moderate downstream concurrency; watch for queueing if latency rises |
| 11-no-retry-standard-timeout-progressive-degradation | 3880 | 59 | 3821 | 0.9848 | 1.9 | 260/261 | 251/257 | 3880 | 1 | 40 | moderate downstream concurrency; watch for queueing if latency rises |
| 12-immediate-retry-progressive-degradation | 1500 | 60 | 1440 | 0.96 | 1.91 | 260/260 | 251/260 | 4380 | 2.92 | 43 | moderate downstream concurrency; watch for queueing if latency rises |
| 13-jitter-progressive-degradation | 1198 | 60 | 1138 | 0.9499 | 1.88 | 260/260 | 251/260 | 3474 | 2.9 | 40 | moderate downstream concurrency; watch for queueing if latency rises |

Interpretation notes belong in `docs/brief-post.md`; this file is the mechanical comparison table.
