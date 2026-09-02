[CmdletBinding()]
param(
    [ValidateSet('start', 'stop', 'status')]
    [string]$Action = 'start',
    [int]$Port = 8081,
    [string]$Profile = 'perf',
    [string]$FaultPoint = 'none'
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
. (Join-Path $PSScriptRoot 'import-local-env.ps1')
Import-LocalEnv -Path (Join-Path $root '.env')
$target = Join-Path $root 'target'
$pidDirectory = Join-Path $target 'test-pids'
$pidFile = Join-Path $pidDirectory "backend-$Port.pid"
$outLog = Join-Path $target 'test-backend.out.log'
$errLog = Join-Path $target 'test-backend.err.log'

function Test-Listening([int]$PortNumber) {
    return [bool](Get-NetTCPConnection -LocalPort $PortNumber -State Listen -ErrorAction SilentlyContinue)
}

function Wait-Listening([int]$PortNumber, [int]$TimeoutSeconds = 180) {
    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-Listening $PortNumber) { return }
        Start-Sleep -Seconds 2
    }
    throw "Timed out waiting for port $PortNumber. See $errLog"
}

function Start-TestEnvironment {
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        $dockerDesktop = 'C:\Program Files\Docker\Docker\Docker Desktop.exe'
        if (-not (Test-Path $dockerDesktop)) { throw 'Docker daemon is not available and Docker Desktop was not found.' }
        Write-Host 'Docker daemon is not ready; starting Docker Desktop...'
        Start-Process -FilePath $dockerDesktop -WindowStyle Hidden | Out-Null
        $deadline = (Get-Date).AddMinutes(3)
        do {
            Start-Sleep -Seconds 5
            docker info *> $null
            if ($LASTEXITCODE -eq 0) { break }
        } while ((Get-Date) -lt $deadline)
        if ($LASTEXITCODE -ne 0) { throw 'Docker daemon did not become ready.' }
    }

    Push-Location $root
    try {
        docker compose up -d
        if ($LASTEXITCODE -ne 0) { throw 'Docker Compose failed to start middleware.' }
    } finally { Pop-Location }

    Wait-Listening 3307 120
    Wait-Listening 6379 120
    Wait-Listening 9092 120

    if (Test-Listening $Port) {
        if ($FaultPoint -ne 'none') {
            throw "Backend port $Port is already in use; cannot start fault-injection instance."
        }
        Write-Host "Backend already listening on port $Port."
        return
    }

    New-Item -ItemType Directory -Path $pidDirectory -Force | Out-Null
    $runArguments = "--server.port=$Port"
    if ($FaultPoint -ne 'none') {
        $runArguments += " --app.fault-injection.enabled=true --app.fault-injection.point=$FaultPoint"
    }
    $arguments = @(
        'spring-boot:run',
        "-Dspring-boot.run.profiles=$Profile",
        "-Dspring-boot.run.arguments=`"$runArguments`""
    )
    $process = Start-Process -FilePath 'mvn.cmd' -ArgumentList $arguments -WorkingDirectory $root `
        -RedirectStandardOutput $outLog -RedirectStandardError $errLog -WindowStyle Hidden -PassThru
    $process.Id | Set-Content -LiteralPath $pidFile
    Wait-Listening $Port
    Write-Host "Backend test environment ready on http://localhost:$Port"
}

function Stop-TestEnvironment {
    if (Test-Path $pidFile) {
        $processId = [int](Get-Content -Raw $pidFile).Trim()
        $allProcesses = @(Get-CimInstance Win32_Process)
        $pending = @($processId)
        $descendantIds = New-Object System.Collections.Generic.List[int]
        while ($pending.Count -gt 0) {
            $parentId = $pending[0]
            if ($pending.Count -eq 1) { $pending = @() } else { $pending = @($pending[1..($pending.Count - 1)]) }
            foreach ($child in $allProcesses | Where-Object { $_.ParentProcessId -eq $parentId }) {
                if (-not $descendantIds.Contains([int]$child.ProcessId)) {
                    $descendantIds.Add([int]$child.ProcessId)
                    $pending += [int]$child.ProcessId
                }
            }
        }
        foreach ($childId in ($descendantIds | Sort-Object -Descending)) {
            Stop-Process -Id $childId -Force -ErrorAction SilentlyContinue
        }
        Stop-Process -Id $processId -Force -ErrorAction SilentlyContinue
        Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
    }
    $deadline = (Get-Date).AddSeconds(20)
    while ((Get-Date) -lt $deadline -and (Test-Listening $Port)) {
        Start-Sleep -Milliseconds 500
    }
    if (Test-Listening $Port) { throw "Timed out waiting for backend port $Port to close." }
    Write-Host 'Tracked test backend stopped. Docker middleware was left running.'
}

switch ($Action) {
    'start' { Start-TestEnvironment }
    'stop' { Stop-TestEnvironment }
    'status' {
        docker compose ps
        Write-Host "Backend ${Port}: $(if (Test-Listening $Port) { 'listening' } else { 'not listening' })"
    }
}
