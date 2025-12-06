# Crypto Arbitrage - Script d'arrêt
# Ce script arrête tous les services

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Crypto Arbitrage - Arret" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# Arrêter les containers Docker
Write-Host "Arret de Docker (MySQL + phpMyAdmin)..." -ForegroundColor Yellow
Set-Location -Path $PSScriptRoot
docker-compose down

Write-Host ""
Write-Host "Containers arretes." -ForegroundColor Green
Write-Host ""
Write-Host "Note: Les fenetres Backend et Frontend doivent etre fermees manuellement." -ForegroundColor Yellow
Write-Host ""
