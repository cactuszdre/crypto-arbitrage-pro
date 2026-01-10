# Phase 1 Recap: Backend Microservices Architecture

## Overview
Successfully transitioned from a monolithic Spring Boot backend to a microservices architecture involving two distinct services. This separation ensures scalability, better resource management, and cleaner code organization.

## Completed Objectives

### 1. Service Creation & Separation
- **Funding Service (Port 8081)**
  - **Responsibility:** Raw data ingestion, real-time fetching, and managing the core database tables.
  - **Scheduler:** Transferred and activated `FundingRateScheduler` to fetch data every 10 seconds.
  - **API:** Provides raw funding rates, DEX info, and chat functionality.
  
- **Arbitrage Service (Port 8082)**
  - **Responsibility:** Analytics, historical data aggregation, charts, and arbitrage opportunity detection.
  - **Database:** Connects to the same PostgreSQL instance (Shared Database Pattern) for efficient read-only analytics.
  - **API:** Exposes optimized endpoints for charts (`/api/charts`) and opportunities (`/api/arbitrage`).

### 2. Technology Migration
- **Spring MVC -> Jersey (JAX-RS)**
  - Migrated all Controllers to JAX-RS Resources.
  - Configured `JerseyConfig` for endpoint registration.
  - Standardized error handling via JSON responses.

- **Refactoring**
  - Renamed `service` package to `business` to avoid naming conflicts and clarify intent.
  - Implemented **MapStruct** for efficient Entity-to-DTO mapping (`FundingRateMapper`).
  - Removed duplicated logic: Ingestion logic remains in `funding-service`, Analytics logic moved to `arbitrage-service`.

- **Documentation**
  - Added **SpringDoc OpenAPI** (Swagger UI) for automated API documentation.

### 3. Frontend Integration
- **Intelligent Routing:** Updated `backend.ts` in Nuxt to route requests based on path context:
  - `/api/arbitrage/*` -> Port 8082
  - `/api/charts/*` -> Port 8082
  - Other -> Port 8081

### 4. Stability fixes
- **Transactional Consistency:** Added `@Transactional(readOnly = true)` to business services to prevent `LazyInitializationException` on shared entities.
- **Microservice Configuration:** Disabled strict schema validation (`ddl-auto=none`) on `arbitrage-service` to allow shared database usage without conflicts.

## Next Steps (Phase 2)
The foundation is solid. We are ready to proceed with **security and quality improvements**.

### Upcoming Tasks
1. **Security:** Implement JWT with Role-Based Access Control (Guest/Client/Admin).
2. **Quality:** Set up JaCoCo for code coverage and implement comprehensive unit tests.
3. **Robustness:** Add a Global Exception Handler for Jersey to standardize error layouts.
