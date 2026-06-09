$ErrorActionPreference = "Stop"

$root = Split-Path -Parent $PSScriptRoot
$frontend = Join-Path $root "src\frontend"
$target = Join-Path $root "target"

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

if (-not (Test-Path $target)) {
    New-Item -ItemType Directory -Path $target | Out-Null
}

Write-Host "Checking Docker daemon..."
docker info *> $null
if ($LASTEXITCODE -ne 0) {
    $dockerDesktop = "C:\Program Files\Docker\Docker\Docker Desktop.exe"
    if (-not (Test-Path $dockerDesktop)) {
        throw "Docker daemon is not running and Docker Desktop was not found."
    }

    Start-Process -FilePath $dockerDesktop -WindowStyle Hidden
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
docker compose up -d
Pop-Location

Wait-Port -Port 3307
Wait-Port -Port 6379
Wait-Port -Port 9092

if (-not (Test-PortListening -Port 8080)) {
    Write-Host "Starting Spring Boot on http://localhost:8080 ..."
    Start-Process -FilePath (Join-Path $root "mvnw.cmd") `
        -ArgumentList "spring-boot:run" `
        -WorkingDirectory $root `
        -RedirectStandardOutput (Join-Path $target "app-run.log") `
        -RedirectStandardError (Join-Path $target "app-run.err.log") `
        -WindowStyle Hidden
    Wait-Port -Port 8080 -TimeoutSeconds 180
}

if (-not (Test-PortListening -Port 5173)) {
    Write-Host "Starting frontend on http://localhost:5173 ..."
    Start-Process -FilePath "npm.cmd" `
        -ArgumentList "run", "dev", "--", "--host", "0.0.0.0" `
        -WorkingDirectory $frontend `
        -RedirectStandardOutput (Join-Path $target "frontend-vite.log") `
        -RedirectStandardError (Join-Path $target "frontend-vite.err.log") `
        -WindowStyle Hidden
    Wait-Port -Port 5173
}

Write-Host ""
Write-Host "Local dev environment is ready:"
Write-Host "  Frontend: http://localhost:5173"
Write-Host "  Backend:  http://localhost:8080"
Write-Host ""
Write-Host "Useful logs:"
Write-Host "  $target\app-run.log"
Write-Host "  $target\app-run.err.log"
Write-Host "  $target\frontend-vite.log"
Write-Host "  $target\frontend-vite.err.log"
