# Crypto Arbitrage - Script de démarrage
# Ce script lance le backend Spring Boot et le frontend Nuxt

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Crypto Arbitrage - Demarrage" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Configuration
$frontendDir = "frontend"
$frontendName = "Nuxt"

# Vérifier les fichiers .env
Write-Host "[0/4] Verification des fichiers .env..." -ForegroundColor Yellow

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
Write-Host "[1/4] Verification de Docker..." -ForegroundColor Yellow
$dockerStatus = docker info 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: Docker Desktop n'est pas lance. Veuillez le demarrer." -ForegroundColor Red
    exit 1
}
Write-Host "Docker OK" -ForegroundColor Green

# Lancer les containers Docker (PostgreSQL + pgAdmin)
Write-Host ""
Write-Host "[2/4] Demarrage de PostgreSQL..." -ForegroundColor Yellow
Set-Location -Path $PSScriptRoot
docker-compose --env-file ./backend/.env up -d
Write-Host "PostgreSQL: localhost:5432" -ForegroundColor Green
Write-Host "pgAdmin: http://localhost:5050" -ForegroundColor Green

# Attendre que PostgreSQL soit prêt
Write-Host ""
Write-Host "[3/4] Attente de PostgreSQL..." -ForegroundColor Yellow
Start-Sleep -Seconds 10

# Lancer le Backend et Frontend
Write-Host ""
Write-Host "[4/4] Demarrage du Backend et Frontend..." -ForegroundColor Yellow

# Backend Spring Boot
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot\backend'; Write-Host 'Backend Spring Boot' -ForegroundColor Cyan; .\mvnw.cmd spring-boot:run"

# Frontend Nuxt
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot\$frontendDir'; Write-Host 'Frontend $frontendName' -ForegroundColor Cyan; npm run dev"

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "   Tous les services sont lances!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "URLs:" -ForegroundColor Cyan
Write-Host "  Frontend:   http://localhost:5173" -ForegroundColor White
Write-Host "  Backend:    http://localhost:8080/api" -ForegroundColor White
Write-Host "  pgAdmin:    http://localhost:5050" -ForegroundColor White
Write-Host ""
