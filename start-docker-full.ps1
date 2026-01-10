# Start Crypto Arbitrage in FULL DOCKER Mode
# Usage: ./start-docker-full.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "   Crypto Arbitrage - FULL DOCKER MODE" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "This will build and run ALL services (DB + Backend) in Docker containers."
Write-Host "First run may take a few minutes to compile Maven projects."
Write-Host ""

# Check .env
if (-not (Test-Path "backend\.env")) {
    Write-Host "ERREUR: backend\.env n'existe pas!" -ForegroundColor Red
    exit 1
}

# Check Docker
docker info > $null 2>&1
if ($LASTEXITCODE -ne 0) {
    Write-Host "ERREUR: Docker Desktop n'est pas lance." -ForegroundColor Red
    exit 1
}

Write-Host "Starting services... (Press Ctrl+C to stop)" -ForegroundColor Yellow
docker-compose -f docker-compose-full.yml --env-file backend/.env up --build
