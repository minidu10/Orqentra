# Starts the whole Orqentra stack: infrastructure, six services, and the web UI.
#
#   .\dev.ps1            # start everything
#   .\dev.ps1 -Stop      # stop the services and the UI (containers keep running)
#
# Each service opens in its own PowerShell window so its logs stay readable and any one
# of them can be restarted on its own.

param(
    [switch]$Stop,
    [switch]$SkipDocker
)

$ErrorActionPreference = "Stop"
$root = $PSScriptRoot

if ($Stop) {
    Write-Host "Stopping services and the dev server..." -ForegroundColor Cyan
    Get-CimInstance Win32_Process -Filter "Name='java.exe'" |
        Where-Object { $_.CommandLine -match 'ServiceApplication|GatewayApplication|spring-boot:run' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Get-CimInstance Win32_Process -Filter "Name='node.exe'" |
        Where-Object { $_.CommandLine -match 'vite' } |
        ForEach-Object { Stop-Process -Id $_.ProcessId -Force -ErrorAction SilentlyContinue }
    Write-Host "Stopped. Containers are still up; use 'docker compose down' to stop those."
    return
}

if (-not $SkipDocker) {
    Write-Host "Starting infrastructure (Postgres x4, Redpanda, console, Redis)..." -ForegroundColor Cyan
    docker compose -f (Join-Path $root "docker-compose.yml") up -d | Out-Null
}

# Order matters only in that the gateway is easier to read last; the services do not
# depend on each other at startup.
$services = @(
    @{ Name = "auth-service";         Port = 8083 },
    @{ Name = "inventory-service";    Port = 8081 },
    @{ Name = "payment-service";      Port = 8082 },
    @{ Name = "order-service";        Port = 8084 },
    @{ Name = "notification-service"; Port = 8085 },
    @{ Name = "api-gateway";          Port = 8080 }
)

foreach ($service in $services) {
    $path = Join-Path $root $service.Name
    Write-Host ("Starting {0} on {1}..." -f $service.Name, $service.Port) -ForegroundColor Cyan
    Start-Process powershell -ArgumentList @(
        "-NoExit", "-Command",
        "Set-Location '$path'; Write-Host '$($service.Name) :$($service.Port)'; .\mvnw.cmd spring-boot:run"
    ) | Out-Null
    Start-Sleep -Seconds 2
}

$web = Join-Path $root "web"
if (-not (Test-Path (Join-Path $web "node_modules"))) {
    Write-Host "Installing web dependencies..." -ForegroundColor Cyan
    Push-Location $web; npm install; Pop-Location
}

Write-Host "Starting the web UI on 5173..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList @(
    "-NoExit", "-Command", "Set-Location '$web'; npm run dev"
) | Out-Null

Write-Host ""
Write-Host "Everything is starting. Services take about 20 seconds to be ready." -ForegroundColor Green
Write-Host ""
Write-Host "  UI               http://localhost:5173"
Write-Host "  API gateway      http://localhost:8080"
Write-Host "  Redpanda console http://localhost:8090"
Write-Host ""
Write-Host "Seeded accounts (dev only):"
Write-Host "  alice@orqentra.test / alice-pass   RESTAURANT  Alice Trattoria"
Write-Host "  bob@orqentra.test   / bob-pass     RESTAURANT  Bob Bistro"
Write-Host "  admin@orqentra.test / admin-pass   ADMIN       sees the Admin screens"
