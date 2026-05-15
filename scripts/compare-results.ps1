$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$rawDir = Join-Path $root "results/raw"
$csvPath = Join-Path $root "results/comparison.csv"
$mdPath = Join-Path $root "results/comparison.md"
$invariant = [System.Globalization.CultureInfo]::InvariantCulture

New-Item -ItemType Directory -Force -Path (Join-Path $root "results") | Out-Null

$rows = Get-ChildItem $rawDir -Filter "*-app-metrics.json" -ErrorAction SilentlyContinue | Sort-Object Name | ForEach-Object {
    $name = $_.BaseName -replace "-app-metrics$", ""
    $summaryPath = Join-Path $rawDir "$name-summary.json"
    $app = Get-Content -Raw $_.FullName | ConvertFrom-Json
    $summary = if (Test-Path $summaryPath) { Get-Content -Raw $summaryPath | ConvertFrom-Json } else { $null }
    $k6FailedRate = ""
    if ($summary) {
        $metric = $summary.metrics.PSObject.Properties["http_req_failed"].Value
        if ($metric) {
            $k6FailedRate = ([math]::Round([double]$metric.value, 4)).ToString("0.####", $invariant)
        }
    }

    $policyScenario = $name -replace "^\d+-", ""
    [pscustomobject]@{
        run = $name
        policy_scenario = $policyScenario
        total_requests = [long]$app.totalRequests
        successful_requests = [long]$app.successfulRequests
        failed_requests = [long]$app.failedRequests
        error_rate = ([math]::Round([double]$app.errorRate, 4)).ToString("0.####", $invariant)
        successful_requests_per_second = ([math]::Round([double]$app.successfulRequestsPerSecond, 2)).ToString("0.##", $invariant)
        all_attempt_p95_ms = [long]$app.allAttemptLatencyP95Ms
        all_attempt_p99_ms = [long]$app.allAttemptLatencyP99Ms
        successful_request_p95_ms = [long]$app.successLatencyP95Ms
        successful_request_p99_ms = [long]$app.successLatencyP99Ms
        downstream_calls = [long]$app.downstreamCalls
        retry_amplification_factor = ([math]::Round([double]$app.retryAmplificationFactor, 3)).ToString("0.###", $invariant)
        max_inflight_downstream = [int]$app.maxInflightDownstream
        saturation_observation = [string]$app.saturationObservation
        k6_http_req_failed_rate = $k6FailedRate
    }
}

$rows | Export-Csv -Encoding UTF8 $csvPath

$lines = @()
$lines += "# Retry Lab Comparison"
$lines += ""
$lines += 'Generated from local simulation data in `results/raw`. Do not treat these numbers as production evidence.'
$lines += ""
$lines += "| run | total | success | failed | error rate | success rps | attempt p95/p99 ms | success p95/p99 ms | downstream calls | amplification | max inflight | observation |"
$lines += "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|"
foreach ($row in $rows) {
    $lines += "| $($row.run) | $($row.total_requests) | $($row.successful_requests) | $($row.failed_requests) | $($row.error_rate) | $($row.successful_requests_per_second) | $($row.all_attempt_p95_ms)/$($row.all_attempt_p99_ms) | $($row.successful_request_p95_ms)/$($row.successful_request_p99_ms) | $($row.downstream_calls) | $($row.retry_amplification_factor) | $($row.max_inflight_downstream) | $($row.saturation_observation) |"
}
$lines += ""
$lines += 'Interpretation notes belong in `docs/brief-post.md`; this file is the mechanical comparison table.'
$lines | Set-Content -Encoding UTF8 $mdPath

Write-Host "Wrote $csvPath"
Write-Host "Wrote $mdPath"
