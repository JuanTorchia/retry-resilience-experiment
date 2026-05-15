param(
    [ValidateSet("smoke", "editorial")]
    [string]$Mode = "smoke",
    [switch]$KeepContainers
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
Push-Location $root
try {
    docker compose up -d --build retry-lab
    & "$PSScriptRoot/run-all.ps1" -Mode $Mode -UseDockerK6
} finally {
    if (-not $KeepContainers) {
        docker compose down
    }
    Pop-Location
}
