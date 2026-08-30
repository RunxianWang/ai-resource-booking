param(
    [ValidateSet("start", "stop", "status", "logs")]
    [string]$Action = "start"
)

$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $root "src\frontend"
$target = Join-Path $root "target"
$pidDirectory = Join-Path $target "dev-pids"

function Test-PortListening {
    param([int]$Port)
    return [bool](Get-NetTCPConnection -LocalPort $Port -State Listen -ErrorAction SilentlyContinue)
}

function Wait-Port {
    param(
        [int]$Port,
        [int]$TimeoutSeconds = 120
    )

    $deadline = (Get-Date).AddSeconds($TimeoutSeconds)
    while ((Get-Date) -lt $deadline) {
        if (Test-PortListening -Port $Port) {
            return
        }
        Start-Sleep -Seconds 2
    }

    throw "Timed out waiting for port $Port."
}

function Require-Command {
    param([string]$Name)
    if (-not (Get-Command $Name -ErrorAction SilentlyContinue)) {
        throw "Required command '$Name' was not found in PATH."
    }
}

function Get-PidFile {
    param([string]$Name)
    return (Join-Path $pidDirectory "$Name.pid")
}

function Stop-TrackedProcess {
    param([string]$Name)

    $pidFile = Get-PidFile -Name $Name
    if (-not (Test-Path $pidFile)) {
        return
    }

    $processId = [int](Get-Content $pidFile -Raw).Trim()
    $process = Get-Process -Id $processId -ErrorAction SilentlyContinue
    if ($null -ne $process) {
        Write-Host "Stopping $Name (PID $processId)..."
        Stop-Process -Id $processId -Force
    }

    Remove-Item -LiteralPath $pidFile -Force -ErrorAction SilentlyContinue
}

function Show-PortStatus {
    param([int]$Port)

    if (Test-PortListening -Port $Port) {
        Write-Host "  ${Port}: listening"
    } else {
        Write-Host "  ${Port}: not listening"
    }
}

function Start-Dependencies {
    Require-Command -Name "docker"

    Write-Host "Checking Docker daemon..."
    docker info *> $null
    if ($LASTEXITCODE -ne 0) {
        $dockerDesktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
        if (-not (Test-Path $dockerDesktop)) {
            throw "Docker daemon is not running and Docker Desktop was not found."
        }

        Write-Host "Starting Docker Desktop..."
        Start-Process -FilePath $dockerDesktop -WindowStyle Hidden | Out-Null
        $deadline = (Get-Date).AddMinutes(3)
        do {
            Start-Sleep -Seconds 5
            docker info *> $null
            if ($LASTEXITCODE -eq 0) {
                break
            }
        } while ((Get-Date) -lt $deadline)

        if ($LASTEXITCODE -ne 0) {
            throw "Docker daemon did not become ready."
        }
    }

    Write-Host "Starting MySQL, Redis, and Kafka..."
    Push-Location $root
    try {
        docker compose up -d
        if ($LASTEXITCODE -ne 0) {
            throw "Docker Compose failed to start the middleware."
        }
    } finally {
        Pop-Location
    }

    Wait-Port -Port 3307
    Wait-Port -Port 6379
    Wait-Port -Port 9092
}

function Start-Application {
    if (-not (Test-Path $target)) {
        New-Item -ItemType Directory -Path $target | Out-Null
    }
    if (-not (Test-Path $pidDirectory)) {
        New-Item -ItemType Directory -Path $pidDirectory | Out-Null
    }

    Require-Command -Name "java"
    Require-Command -Name "npm"

    if (-not (Test-PortListening -Port 8080)) {
        Write-Host "Starting Spring Boot on http://localhost:8080 ..."
        $backend = Start-Process -FilePath (Join-Path $root "mvnw.cmd") `
            -ArgumentList "spring-boot:run" `
            -WorkingDirectory $root `
            -RedirectStandardOutput (Join-Path $target "app-run.log") `
            -RedirectStandardError (Join-Path $target "app-run.err.log") `
            -WindowStyle Hidden `
            -PassThru
        $backend.Id | Set-Content -LiteralPath (Get-PidFile -Name "backend")
        Wait-Port -Port 8080 -TimeoutSeconds 180
    } else {
        Write-Host "Backend is already listening on port 8080."
    }

    if (-not (Test-PortListening -Port 5173)) {
        Write-Host "Starting frontend on http://localhost:5173 ..."
        $frontendProcess = Start-Process -FilePath "npm.cmd" `
            -ArgumentList "run", "dev", "--", "--host", "0.0.0.0" `
            -WorkingDirectory $frontend `
            -RedirectStandardOutput (Join-Path $target "frontend-vite.log") `
            -RedirectStandardError (Join-Path $target "frontend-vite.err.log") `
            -WindowStyle Hidden `
            -PassThru
        $frontendProcess.Id | Set-Content -LiteralPath (Get-PidFile -Name "frontend")
        Wait-Port -Port 5173
    } else {
        Write-Host "Frontend is already listening on port 5173."
    }
}

function Start-LocalDev {
    Start-Dependencies
    Start-Application

    Write-Host ""
    Write-Host "Local dev environment is ready:"
    Write-Host "  Frontend: http://localhost:5173"
    Write-Host "  Backend:  http://localhost:8080"
    Write-Host ""
    Write-Host "Use '.\scripts\start-local-dev.ps1 status' to inspect the environment."
}

function Stop-LocalDev {
    Stop-TrackedProcess -Name "frontend"
    Stop-TrackedProcess -Name "backend"

    Require-Command -Name "docker"
    Push-Location $root
    try {
        Write-Host "Stopping Docker middleware..."
        docker compose down
    } finally {
        Pop-Location
    }

    Write-Host "Stopped. Docker volumes were preserved."
}

function Show-Status {
    Require-Command -Name "docker"

    Write-Host "Docker containers:"
    Push-Location $root
    try {
        docker compose ps
    } finally {
        Pop-Location
    }

    Write-Host ""
    Write-Host "Ports:"
    Show-PortStatus -Port 3307
    Show-PortStatus -Port 6379
    Show-PortStatus -Port 9092
    Show-PortStatus -Port 8080
    Show-PortStatus -Port 5173
}

function Show-Logs {
    Write-Host "Log files:"
    Write-Host "  $target\app-run.log"
    Write-Host "  $target\app-run.err.log"
    Write-Host "  $target\frontend-vite.log"
    Write-Host "  $target\frontend-vite.err.log"
    Write-Host ""

    foreach ($log in @("app-run.err.log", "frontend-vite.err.log")) {
        $path = Join-Path $target $log
        if (Test-Path $path) {
            Write-Host "--- $log (last 20 lines) ---"
            Get-Content -LiteralPath $path -Tail 20
        }
    }
}

switch ($Action) {
    "start"  { Start-LocalDev }
    "stop"   { Stop-LocalDev }
    "status" { Show-Status }
    "logs"   { Show-Logs }
}
