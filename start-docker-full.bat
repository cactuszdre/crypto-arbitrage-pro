@echo off
echo ========================================
echo    Crypto Arbitrage - FULL DOCKER MODE
echo ========================================
echo.

if not exist "backend\.env" (
    echo ERREUR: backend\.env n'existe pas!
    pause
    exit /b 1
)

docker info >nul 2>&1
if %errorlevel% neq 0 (
    echo ERREUR: Docker Desktop n'est pas lance.
    pause
    exit /b 1
)

echo Starting services... (Press Ctrl+C to stop)
docker-compose -f docker-compose-full.yml --env-file backend/.env up --build
pause
