[CmdletBinding()]
param(
    [ValidateSet('Fast', 'Full', 'Resilience', 'Load')]
    [string]$Suite = 'Fast',
    [string]$BaseUrl = 'http://localhost:8081',
    [int]$SlotId = 990001,
    [int]$Capacity = 100,
    [int]$Requests = 500,
    [int]$Concurrency = 100,
    [switch]$SkipFrontend
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'import-local-env.ps1')
Import-LocalEnv -Path (Join-Path $repoRoot '.env')
$timestamp = Get-Date -Format 'yyyyMMdd-HHmmss'
$outputDir = Join-Path $repoRoot "test-results\backend\$timestamp"
$steps = [System.Collections.Generic.List[object]]::new()
$runState = [pscustomobject]@{ startedTestBackend = $false }

New-Item -ItemType Directory -Path $outputDir -Force | Out-Null

function Invoke-Step {
    param(
        [Parameter(Mandatory = $true)][string]$Name,
        [Parameter(Mandatory = $true)][scriptblock]$Action
    )

    $started = Get-Date
    Write-Host "`n=== $Name ===" -ForegroundColor Cyan
    try {
        & $Action
        $steps.Add([pscustomobject]@{ name = $Name; status = 'PASS'; seconds = ((Get-Date) - $started).TotalSeconds })
        Write-Host "PASS $Name" -ForegroundColor Green
    } catch {
        $steps.Add([pscustomobject]@{ name = $Name; status = 'FAIL'; seconds = ((Get-Date) - $started).TotalSeconds; error = $_.Exception.Message })
        Write-Host "FAIL $Name`n$($_.Exception.Message)" -ForegroundColor Red
        throw
    }
}

function Invoke-PythonTest {
    param([string]$Script, [string[]]$Arguments)
    Push-Location $repoRoot
    try {
        & python $Script @Arguments
        if ($LASTEXITCODE -ne 0) { throw "python $Script exited with code $LASTEXITCODE" }
    } finally {
        Pop-Location
    }
}

try {
    if ($Suite -in @('Full', 'Load')) {
        Invoke-Step 'Start backend test environment' {
            $backendWasListening = [bool](Get-NetTCPConnection -LocalPort 8081 -State Listen -ErrorAction SilentlyContinue)
            Push-Location $repoRoot
            try {
                & .\scripts\start-backend-test-env.ps1 -Action start -Port 8081 -Profile perf
                if ($LASTEXITCODE -ne 0) { throw "Test environment exited with code $LASTEXITCODE" }
                $runState.startedTestBackend = -not $backendWasListening
            } finally { Pop-Location }
        }
    }

    Invoke-Step 'Backend unit tests' {
        Push-Location $repoRoot
        try {
            if ($Suite -eq 'Fast') {
                & mvn -q '-Dtest=JwtServiceTest,BookingEventHandlerTest' test
            } else {
                & mvn -q test
            }
            if ($LASTEXITCODE -ne 0) { throw "Maven tests exited with code $LASTEXITCODE" }
        } finally { Pop-Location }
    }

    if (-not $SkipFrontend) {
        Invoke-Step 'Frontend lint and production build' {
            Push-Location (Join-Path $repoRoot 'src\frontend')
            try {
                & npm run lint
                if ($LASTEXITCODE -ne 0) { throw "npm run lint exited with code $LASTEXITCODE" }
                & npm run build
                if ($LASTEXITCODE -ne 0) { throw "npm run build exited with code $LASTEXITCODE" }
            } finally { Pop-Location }
        }
    }

    if ($Suite -in @('Full', 'Load')) {
        Invoke-Step 'Business boundary rules' {
            Invoke-PythonTest 'scripts/test_today_booking_rules.py' @('--base-url', $BaseUrl, '--perf')
        }

        Invoke-Step 'Concurrent booking and consistency' {
            Invoke-PythonTest 'scripts/test_base_chain.py' @(
                '--base-url', $BaseUrl,
                '--slot-id', $SlotId,
                '--capacity', $Capacity,
                '--requests', $(if ($Suite -eq 'Load') { $Requests } else { 500 }),
                '--concurrency', $(if ($Suite -eq 'Load') { $Concurrency } else { 100 }),
                '--output-root', 'test-results/backend/concurrent-booking'
            )
        }

        Invoke-Step 'Concurrent duplicate booking and cancellation' {
            Invoke-PythonTest 'scripts/test_base_chain_edges.py' @(
                '--base-url', $BaseUrl,
                '--slot-id', $SlotId,
                '--capacity', $Capacity
            )
        }

    }

    if ($Suite -in @('Full', 'Resilience', 'Load')) {
        if ($runState.startedTestBackend) {
            & .\scripts\start-backend-test-env.ps1 -Action stop -Port 8081 -Profile perf
            $runState.startedTestBackend = $false
        }
        Invoke-Step 'Kafka Redis Outbox DLT and Replay resilience' {
            Invoke-PythonTest 'scripts/test_backend_resilience.py' @()
        }
    }

    if ($runState.startedTestBackend) {
        & .\scripts\start-backend-test-env.ps1 -Action stop -Port 8081 -Profile perf
        $runState.startedTestBackend = $false
    }

    $steps | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $outputDir 'steps.json')
    @(
        '# Backend verification report',
        '',
        "- Suite: $Suite",
        "- Base URL: $BaseUrl",
        "- Started: $timestamp",
        '',
        '| Step | Status | Seconds |',
        '|---|---|---:|',
        ($steps | ForEach-Object { "| $($_.name) | $($_.status) | $([math]::Round($_.seconds, 2)) |" }),
        '',
        "Detailed outputs are under $outputDir and the individual test output directories."
    ) | Set-Content -Encoding UTF8 (Join-Path $outputDir 'report.md')
    Write-Host "`nBackend verification passed. Report: $outputDir\report.md" -ForegroundColor Green
} catch {
    if ($runState.startedTestBackend) {
        & .\scripts\start-backend-test-env.ps1 -Action stop -Port 8081 -Profile perf
    }
    $steps | ConvertTo-Json -Depth 5 | Set-Content -Encoding UTF8 (Join-Path $outputDir 'steps.json')
    @(
        '# Backend verification report',
        '',
        "- Suite: $Suite",
        "- Base URL: $BaseUrl",
        "- Started: $timestamp",
        '',
        '| Step | Status | Seconds |',
        '|---|---|---:|',
        ($steps | ForEach-Object { "| $($_.name) | $($_.status) | $([math]::Round($_.seconds, 2)) |" }),
        '',
        "Failure: $($_.Exception.Message)"
    ) | Set-Content -Encoding UTF8 (Join-Path $outputDir 'report.md')
    Write-Error "Backend verification failed. Report: $outputDir\report.md"
    exit 1
}
