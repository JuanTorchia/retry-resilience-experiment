param(
    [string]$BaseUrl = "http://localhost:18080",
    [ValidateSet("smoke", "editorial", "custom")]
    [string]$Mode = "smoke",
    [int]$Vus = 0,
    [string]$Duration = "",
    [switch]$UseDockerK6
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$rawDir = Join-Path $root "results/raw"
New-Item -ItemType Directory -Force -Path $rawDir | Out-Null
Remove-Item (Join-Path $rawDir "*.json") -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $root "results/comparison.csv") -Force -ErrorAction SilentlyContinue
Remove-Item (Join-Path $root "results/comparison.md") -Force -ErrorAction SilentlyContinue

if ($Mode -eq "smoke") {
    if ($Vus -le 0) { $Vus = 5 }
    if ([string]::IsNullOrWhiteSpace($Duration)) { $Duration = "5s" }
} elseif ($Mode -eq "editorial") {
    if ($Vus -le 0) { $Vus = 40 }
    if ([string]::IsNullOrWhiteSpace($Duration)) { $Duration = "60s" }
} else {
    if ($Vus -le 0 -or [string]::IsNullOrWhiteSpace($Duration)) {
        throw "Mode custom requiere -Vus y -Duration explicitos"
    }
}

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
    @{ name = "06-no-retry-standard-timeout-random-failures"; policy = "no-retry-standard-timeout"; scenario = "random-failures" },
    @{ name = "07-immediate-retry-random-failures"; policy = "immediate-retry"; scenario = "random-failures" },
    @{ name = "08-exponential-backoff-random-failures"; policy = "exponential-backoff"; scenario = "random-failures" },
    @{ name = "09-jitter-random-failures"; policy = "jitter"; scenario = "random-failures" },
    @{ name = "10-no-retry-standard-timeout-progressive-degradation"; policy = "no-retry-standard-timeout"; scenario = "progressive-degradation" },
    @{ name = "11-immediate-retry-progressive-degradation"; policy = "immediate-retry"; scenario = "progressive-degradation" },
    @{ name = "12-jitter-progressive-degradation"; policy = "jitter"; scenario = "progressive-degradation" },
    @{ name = "13-circuit-breaker-progressive-degradation"; policy = "circuit-breaker"; scenario = "progressive-degradation" },
    @{ name = "14-bulkhead-progressive-degradation"; policy = "bulkhead"; scenario = "progressive-degradation" },
    @{ name = "15-no-retry-standard-timeout-latency-tail-spike"; policy = "no-retry-standard-timeout"; scenario = "latency-tail-spike" },
    @{ name = "16-immediate-retry-latency-tail-spike"; policy = "immediate-retry"; scenario = "latency-tail-spike" },
    @{ name = "17-jitter-latency-tail-spike"; policy = "jitter"; scenario = "latency-tail-spike" }
)

Wait-LabReady -Url $BaseUrl
Write-Host "Mode=$Mode VUs=$Vus Duration=$Duration BaseUrl=$BaseUrl"

foreach ($run in $runs) {
    Write-Host "Running $($run.name)"
    $summaryPath = Join-Path $rawDir "$($run.name)-summary.json"
    Invoke-RestMethod -Method Post -Uri "$BaseUrl/api/reset" -TimeoutSec 10 | Out-Null

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
