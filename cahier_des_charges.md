# Cahier des Charges - Projet Crypto Arbitrage

## 1. Membres d'équipe
- **Steven T.**
- **Baptiste D.**

*(Note : Projet réalisé en binôme)*

## 2. Présentation du sujet
Le projet porte sur la réalisation d'une plateforme de **Crypto Funding Arbitrage**.
L'objectif est de surveiller et d'analyser les taux de financement (Funding Rates) sur différents échanges de crypto-monnaies (DEX).
L'application permet aux utilisateurs de repérer des opportunités d'arbitrage "delta-neutral" en identifiant les écarts de taux entre différentes plateformes pour un même actif.

## 3. Présentation des cas d'utilisation
Les fonctionnalités principales attendues sont :

### Pour l'utilisateur final :
- **Tableau de bord (Dashboard)** : Vue d'ensemble des opportunités d'arbitrage les plus rentables en temps réel.
- **Comparateur de taux** : Visualisation des taux de financement actuels par crypto-actif et par plateforme (DyDx, Hyperliquid, Vertex, etc.).
- **Graphiques historiques** : Consultation de l'évolution des taux et des spreads d'arbitrage sur différentes échelles de temps.
- **Détails par actif** : Page dédiée pour chaque crypto-monnaie affichant les données agrégées de tous les marchés disponibles.

### Pour le système (Backend) :
- **Collecte de données** : Récupération automatisée et périodique des taux via les APIs des échanges.
- **Normalisation** : Harmonisation des données provenant de sources hétérogènes.
- **Détection d'opportunités** : Calcul automatique des spreads (différence entre taux Long et Short max).
- **Historisation** : Stockage optimisé des données pour l'analyse temporelle (agrégations).

## 4. Schéma d'architecture
L'application repose sur une architecture **Microservices** conteneurisée :

- **Frontend** : Application Web développée avec **Nuxt.js (Vue 3)** pour une interface réactive et performante.
- **Backend** : Écosystème **Java Spring Boot** divisé en microservices :
    - **Auth Service** : Gestion de l'authentification et des utilisateurs.
    - **Funding Service** : Responsable de la récupération des données de marché (Scraping/REST) et de leur stockage.
    - **Arbitrage Service** : Moteur de calcul des opportunités et gestion des statistiques d'arbitrage.
- **Base de données** : **PostgreSQL** pour le stockage persistant relationnel.
- **Infrastructure** : Déploiement orchestré par **Docker Compose**.

## 5. Résultat attendu côté mobile
L'application est conçue avec une approche **Responsive Design** pour garantir une expérience fluide sur mobile :
- **Navigation adaptée** : Menu adapté aux écrans tactiles.
- **Tableaux réactifs** : Les grilles de données (DataGrids) sont adaptées pour être consultables sur petits écrans (scroll horizontal ou mode cartes).
- **Graphiques tactiles** : Les courbes de taux sont manipulables au toucher.
- **Performance** : Chargement rapide des données pour une utilisation en mobilité.

## 6. Modélisation de la base de données
Le schéma relationnel s'articule autour des entités clés identifiées dans le projet :

- **Utilisateurs** (`User`, `Role`) : Gestion des accès.
- **Marchés** (`Dex`, `DexMarket`) : Référentiel des plateformes et paires de trading.
- **Données Financières** :
    - `FundingRate` : Données brutes des taux.
    - `ArbitrageOpportunity` : Opportunités calculées (différentiel de taux).
    - `FundingRateAggregations` (5min, Hourly, Daily) : Optimisation pour les graphiques historiques.
