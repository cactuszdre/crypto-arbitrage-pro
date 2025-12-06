# Crypto Arbitrage - Script de démarrage
# Ce script lance le backend Spring Boot et le frontend Vue.js

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Crypto Arbitrage - Demarrage" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Vérifier que Docker est lancé
Write-Host "[1/4] Verification de Docker..." -ForegroundColor Yellow
$dockerStatus = docker info 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: Docker Desktop n'est pas lance. Veuillez le demarrer." -ForegroundColor Red
    exit 1
}
Write-Host "Docker OK" -ForegroundColor Green

# Lancer les containers Docker (MySQL + phpMyAdmin)
Write-Host ""
Write-Host "[2/4] Demarrage de MySQL et phpMyAdmin..." -ForegroundColor Yellow
Set-Location -Path $PSScriptRoot
docker-compose up -d
Write-Host "MySQL: localhost:3306" -ForegroundColor Green
Write-Host "phpMyAdmin: http://localhost:8081" -ForegroundColor Green

# Attendre que MySQL soit prêt
Write-Host ""
Write-Host "[3/4] Attente de MySQL..." -ForegroundColor Yellow
Start-Sleep -Seconds 5

# Lancer le Backend dans une nouvelle fenêtre
Write-Host ""
Write-Host "[4/4] Demarrage du Backend et Frontend..." -ForegroundColor Yellow

# Backend Spring Boot
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot\backend'; Write-Host 'Demarrage du Backend Spring Boot...' -ForegroundColor Cyan; .\mvnw.cmd spring-boot:run"

# Frontend Vue.js
Start-Process powershell -ArgumentList "-NoExit", "-Command", "Set-Location '$PSScriptRoot\frontend'; Write-Host 'Demarrage du Frontend Vue.js...' -ForegroundColor Cyan; npm run dev"

Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "   Tous les services sont lances!" -ForegroundColor Green
Write-Host "========================================" -ForegroundColor Green
Write-Host ""
Write-Host "URLs:" -ForegroundColor Cyan
Write-Host "  Frontend:   http://localhost:5173" -ForegroundColor White
Write-Host "  Backend:    http://localhost:8080/api" -ForegroundColor White
Write-Host "  phpMyAdmin: http://localhost:8081" -ForegroundColor White
Write-Host ""
Write-Host "Credentials phpMyAdmin:" -ForegroundColor Cyan
Write-Host "  User: root" -ForegroundColor White
Write-Host "  Pass: rootpassword" -ForegroundColor White
Write-Host ""
