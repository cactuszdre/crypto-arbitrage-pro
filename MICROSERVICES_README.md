# =============================================
# MICROSERVICES - Quick Test Guide
# =============================================

## 1. Mise à jour du fichier .env du frontend

Ajoutez ces lignes dans frontend/.env :

```
FUNDING_SERVICE_URL=http://localhost:8081
ARBITRAGE_SERVICE_URL=http://localhost:8082
```

## 2. Lancer les microservices

Double-cliquez sur `start-mobile.bat` ou exécutez :

```powershell
.\start-mobile.ps1
```

Cela va lancer :
- PostgreSQL (Docker)
- funding-service (port 8081)
- arbitrage-service (port 8082)
- Frontend Nuxt (port 5173)

## 3. Tester les APIs directement

Funding Service (port 8081):
- GET http://localhost:8081/api/dex
- GET http://localhost:8081/api/funding/rates
- GET http://localhost:8081/api/funding/symbols

Arbitrage Service (port 8082):
- GET http://localhost:8082/api/arbitrage/opportunities
- GET http://localhost:8082/api/arbitrage/rates/all
- GET http://localhost:8082/api/arbitrage/health

## 4. Swagger / OpenAPI

Les specs OpenAPI sont disponibles dans :
- backend/services/funding-service/src/main/resources/openapi.yaml
- backend/services/arbitrage-service/src/main/resources/openapi.yaml

Vous pouvez les visualiser sur https://editor.swagger.io/

## 5. Notes importantes

- L'ancien backend dans backend/src fonctionne toujours (port 8080)
- Les nouveaux microservices sont dans backend/services/
- Pour utiliser l'ancien backend, utilisez start.bat
- Pour utiliser les microservices, utilisez start-mobile.bat
