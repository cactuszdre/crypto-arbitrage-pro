# Crypto Arbitrage - Microservices Version
# Ce script lance les 2 microservices (funding + arbitrage) et le frontend Nuxt

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Crypto Arbitrage - MICROSERVICES" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "  funding-service    -> port 8081" -ForegroundColor DarkGray
Write-Host "  arbitrage-service  -> port 8082" -ForegroundColor DarkGray
Write-Host "  frontend (Nuxt)    -> port 5173" -ForegroundColor DarkGray
Write-Host ""

# Configuration
$frontendDir = "frontend"
$servicesDir = "backend\services"

# Vérifier les fichiers .env
Write-Host "[0/5] Verification des fichiers .env..." -ForegroundColor Yellow

$backendEnv = Join-Path $PSScriptRoot "backend\.env"
if (-not (Test-Path $backendEnv)) {
    Write-Host "ERREUR: backend/.env n'existe pas!" -ForegroundColor Red
    Write-Host "  Copiez backend/.env.example vers backend/.env et configurez vos variables." -ForegroundColor Yellow
    exit 1
}
Write-Host "  backend/.env OK" -ForegroundColor Green

$frontendEnv = Join-Path $PSScriptRoot "$frontendDir\.env"
if (-not (Test-Path $frontendEnv)) {
    Write-Host "ERREUR: $frontendDir/.env n'existe pas!" -ForegroundColor Red
    Write-Host "  Copiez $frontendDir/.env.example vers $frontendDir/.env et configurez vos variables." -ForegroundColor Yellow
    exit 1
}
Write-Host "  $frontendDir/.env OK" -ForegroundColor Green

# Vérifier que Docker est lancé
Write-Host ""
Write-Host "[1/5] Verification de Docker..." -ForegroundColor Yellow
$dockerStatus = docker info 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: Docker Desktop n'est pas lance. Veuillez le demarrer." -ForegroundColor Red
    exit 1
}
Write-Host "Docker OK" -ForegroundColor Green

# Lancer les containers Docker (PostgreSQL)
Write-Host ""
Write-Host "[2/5] Demarrage de PostgreSQL..." -ForegroundColor Yellow
Set-Location -Path $PSScriptRoot
docker-compose --env-file ./backend/.env up -d
Write-Host "PostgreSQL: localhost:5432" -ForegroundColor Green

# Attendre que PostgreSQL soit prêt
Write-Host ""
Write-Host "[3/5] Attente de PostgreSQL..." -ForegroundColor Yellow
Start-Sleep -Seconds 10
Write-Host "PostgreSQL pret!" -ForegroundColor Green

# Lancer Funding Service (port 8081)
Write-Host ""
Write-Host "[4/5] Demarrage des Microservices..." -ForegroundColor Yellow


# Auth Service
Write-Host "  Demarrage auth-service (port 8083)..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", @"
    Set-Location '$PSScriptRoot\$servicesDir\auth-service'
    `$Host.UI.RawUI.WindowTitle = 'AUTH-SERVICE (8083)'
    Write-Host '========================================' -ForegroundColor Blue
    Write-Host '   AUTH SERVICE - Port 8083' -ForegroundColor Blue
    Write-Host '========================================' -ForegroundColor Blue
    ..\mvnw.cmd spring-boot:run
"@

# Funding Service
Write-Host "  Demarrage funding-service (port 8081)..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", @"
    Set-Location '$PSScriptRoot\$servicesDir\funding-service'
    `$Host.UI.RawUI.WindowTitle = 'FUNDING-SERVICE (8081)'
    Write-Host '========================================' -ForegroundColor Magenta
    Write-Host '   FUNDING SERVICE - Port 8081' -ForegroundColor Magenta
    Write-Host '========================================' -ForegroundColor Magenta
    ..\mvnw.cmd spring-boot:run
"@

# Attendre un peu avant de lancer le second service
Start-Sleep -Seconds 5

# Arbitrage Service
Write-Host "  Demarrage arbitrage-service (port 8082)..." -ForegroundColor Cyan
Start-Process powershell -ArgumentList "-NoExit", "-Command", @"
    Set-Location '$PSScriptRoot\$servicesDir\arbitrage-service'
    `$Host.UI.RawUI.WindowTitle = 'ARBITRAGE-SERVICE (8082)'
    Write-Host '========================================' -ForegroundColor Yellow
    Write-Host '   ARBITRAGE SERVICE - Port 8082' -ForegroundColor Yellow
    Write-Host '========================================' -ForegroundColor Yellow
    ..\mvnw.cmd spring-boot:run
"@

# Attendre que les services démarrent
Start-Sleep -Seconds 10

# Lancer le Frontend
Write-Host ""
Write-Host "[5/5] Demarrage du Frontend..." -ForegroundColor Yellow
Start-Process powershell -ArgumentList "-NoExit", "-Command", @"
    Set-Location '$PSScriptRoot\$frontendDir'
    `$Host.UI.RawUI.WindowTitle = 'FRONTEND (5173)'
    Write-Host '========================================' -ForegroundColor Green
    Write-Host '   FRONTEND NUXT - Port 5173' -ForegroundColor Green
    Write-Host '========================================' -ForegroundColor Green
    npm run dev
"@

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "   MICROSERVICES - Tous lances!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "URLs:" -ForegroundColor Cyan
Write-Host "  Frontend:         http://localhost:5173" -ForegroundColor White
Write-Host "  Funding API:      http://localhost:8081/api" -ForegroundColor White
Write-Host "  Arbitrage API:    http://localhost:8082/api" -ForegroundColor White
Write-Host "  Auth API:         http://localhost:8083/api" -ForegroundColor White
Write-Host ""
Write-Host "Swagger (Design First):" -ForegroundColor Cyan
Write-Host "  Funding:          backend/services/funding-service/src/main/resources/openapi.yaml" -ForegroundColor DarkGray
Write-Host "  Arbitrage:        backend/services/arbitrage-service/src/main/resources/openapi.yaml" -ForegroundColor DarkGray
Write-Host ""
Write-Host "ATTENTION: Le frontend doit pointer vers les bons ports!" -ForegroundColor Yellow
Write-Host "  Modifiez frontend/.env pour utiliser:" -ForegroundColor Yellow
Write-Host "    NUXT_PUBLIC_API_BASE_URL=http://localhost:8081" -ForegroundColor White
Write-Host "    NUXT_PUBLIC_ARBITRAGE_API_URL=http://localhost:8082" -ForegroundColor White
Write-Host ""
