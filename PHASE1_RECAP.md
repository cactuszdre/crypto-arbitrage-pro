# Récapitulatif Phase 1 : Architecture Microservices Backend

## Vue d'ensemble
Transition réussie d'un backend monolithique Spring Boot vers une architecture microservices comprenant deux services distincts. Cette séparation assure l'évolutivité, une meilleure gestion des ressources et une organisation du code plus propre.

## Objectifs Atteints

### 1. Création et Séparation des Services
- **Funding Service (Port 8081)**
  - **Responsabilité :** Ingestion des données brutes, récupération en temps réel et gestion des tables de base de données principales.
  - **Planificateur (Scheduler) :** Transfert et activation de `FundingRateScheduler` pour récupérer les données toutes les 10 secondes.
  - **API :** Fournit les taux de financement bruts, les infos DEX et la fonctionnalité de chat.
  
- **Arbitrage Service (Port 8082)**
  - **Responsabilité :** Analytique, agrégation de données historiques, graphiques et détection d'opportunités d'arbitrage.
  - **Base de Données :** Se connecte à la même instance PostgreSQL (Pattern "Shared Database") pour une analytique efficace en lecture seule.
  - **API :** Expose des endpoints optimisés pour les graphiques (`/api/charts`) et les opportunités (`/api/arbitrage`).

### 2. Migration Technologique
- **Spring MVC -> Jersey (JAX-RS)**
  - Migration de tous les Contrôleurs vers des Ressources JAX-RS.
  - Configuration de `JerseyConfig` pour l'enregistrement des endpoints.
  - Standardisation de la gestion des erreurs via des réponses JSON.

- **Refactoring**
  - Renommage du package `service` en `business` pour éviter les conflits de nommage et clarifier l'intention.
  - Implémentation de **MapStruct** pour un mapping efficace Entité-Vers-DTO (`FundingRateMapper`).
  - Suppression de la logique dupliquée : L'ingestion reste dans `funding-service`, l'analytique déplacée vers `arbitrage-service`.

- **Documentation**
  - Ajout de **SpringDoc OpenAPI** (Swagger UI) pour la documentation automatisée de l'API.

### 3. Intégration Frontend
- **Routing Intelligent :** Mise à jour de `backend.ts` dans Nuxt pour diriger les requêtes selon le contexte du chemin :
  - `/api/arbitrage/*` -> Port 8082
  - `/api/charts/*` -> Port 8082
  - Autres -> Port 8081

### 4. Correctifs de Stabilité
- **Cohérence Transactionnelle :** Ajout de `@Transactional(readOnly = true)` aux services métiers pour prévenir les exceptions `LazyInitializationException` sur les entités partagées.
- **Configuration Microservice :** Désactivation de la validation stricte du schéma (`ddl-auto=none`) sur `arbitrage-service` pour permettre l'utilisation de la base de données partagée sans conflits.

## Prochaines Étapes (Phase 2)
Les fondations sont solides. Nous sommes prêts à procéder aux **améliorations de sécurité et de qualité**.

### Tâches à Venir
1. **Sécurité :** Implémenter JWT avec Contrôle d'Accès Basé sur les Rôles (Invité/Client/Admin).
2. **Qualité :** Mettre en place JaCoCo pour la couverture de code et implémenter des tests unitaires complets.
3. **Robustesse :** Ajouter un Gestionnaire d'Exceptions Global pour Jersey afin de standardiser le format des erreurs.
