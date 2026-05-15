param(
    [string]$BaseUrl = "http://localhost:18080",
    [int]$Vus = 40,
    [string]$Duration = "30s",
    [switch]$UseDockerK6
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$rawDir = Join-Path $root "results/raw"
New-Item -ItemType Directory -Force -Path $rawDir | Out-Null
Remove-Item (Join-Path $rawDir "*.json") -Force -ErrorAction SilentlyContinue

function Wait-LabReady {
    param([string]$Url)

    $deadline = (Get-Date).AddSeconds(90)
    do {
        try {
            Invoke-RestMethod -Method Post -Uri "$Url/api/reset" -TimeoutSec 3 | Out-Null
            return
        } catch {
            Start-Sleep -Seconds 2
        }
    } while ((Get-Date) -lt $deadline)

    throw "Retry Lab no respondio en $Url antes de ejecutar la matriz"
}

$runs = @(
    @{ name = "01-no-retry-low-timeout-fixed-delay"; policy = "no-retry-low-timeout"; scenario = "fixed-delay" },
    @{ name = "02-no-retry-standard-timeout-fixed-delay"; policy = "no-retry-standard-timeout"; scenario = "fixed-delay" },
    @{ name = "03-immediate-retry-fixed-delay"; policy = "immediate-retry"; scenario = "fixed-delay" },
    @{ name = "04-exponential-backoff-fixed-delay"; policy = "exponential-backoff"; scenario = "fixed-delay" },
    @{ name = "05-jitter-fixed-delay"; policy = "jitter"; scenario = "fixed-delay" },
    @{ name = "06-circuit-breaker-fixed-delay"; policy = "circuit-breaker"; scenario = "fixed-delay" },
    @{ name = "07-bulkhead-fixed-delay"; policy = "bulkhead"; scenario = "fixed-delay" },
    @{ name = "08-no-retry-standard-timeout-random-failures"; policy = "no-retry-standard-timeout"; scenario = "random-failures" },
    @{ name = "09-immediate-retry-random-failures"; policy = "immediate-retry"; scenario = "random-failures" },
    @{ name = "10-jitter-random-failures"; policy = "jitter"; scenario = "random-failures" },
    @{ name = "11-no-retry-standard-timeout-progressive-degradation"; policy = "no-retry-standard-timeout"; scenario = "progressive-degradation" },
    @{ name = "12-immediate-retry-progressive-degradation"; policy = "immediate-retry"; scenario = "progressive-degradation" },
    @{ name = "13-jitter-progressive-degradation"; policy = "jitter"; scenario = "progressive-degradation" }
)

Wait-LabReady -Url $BaseUrl

foreach ($run in $runs) {
    Write-Host "Running $($run.name)"
    $summaryPath = Join-Path $rawDir "$($run.name)-summary.json"

    if ($UseDockerK6) {
        docker compose run --rm `
            -e BASE_URL="http://retry-lab:18080" `
            -e POLICY="$($run.policy)" `
            -e SCENARIO="$($run.scenario)" `
            -e K6_VUS="$Vus" `
            -e K6_DURATION="$Duration" `
            k6 run --summary-export "/results/raw/$($run.name)-summary.json" /scripts/retry-lab.js
    } else {
        $env:BASE_URL = $BaseUrl
        $env:POLICY = $run.policy
        $env:SCENARIO = $run.scenario
        $env:K6_VUS = "$Vus"
        $env:K6_DURATION = $Duration
        k6 run --summary-export $summaryPath "$root/k6/retry-lab.js"
    }

    $metrics = Invoke-RestMethod -Method Get -Uri "$BaseUrl/api/metrics"
    $metrics | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $rawDir "$($run.name)-app-metrics.json")
}

& "$PSScriptRoot/compare-results.ps1"
