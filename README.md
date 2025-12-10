# CactusFund - Crypto Funding Rate Arbitrage Platform

<div align="center">

![CactusFund Logo](frontend/src/assets/logo.png)

**Discover and execute funding rate arbitrage opportunities across multiple DEXs**

[![Vue.js](https://img.shields.io/badge/Vue.js-3.x-4FC08D?logo=vuedotjs)](https://vuejs.org/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-6DB33F?logo=springboot)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-15-4169E1?logo=postgresql)](https://www.postgresql.org/)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

---

## Overview

CactusFund is a full-stack application that monitors funding rates across decentralized exchanges (DEXs) and identifies arbitrage opportunities. By going long on one exchange and short on another, traders can capture funding rate differentials with minimal directional risk.

### Supported Exchanges

| Exchange | Status | Features |
|----------|--------|----------|
| **Hyperliquid** | ✅ Active | Real-time rates, trade execution |
| **Paradex** | ✅ Active | Real-time rates, trade execution |
| **Lighter** | ✅ Active | Real-time rates, trade execution |

---

## Features

### Core Features
- **Real-time Funding Rates** - Fetches data every 10 seconds from all DEXs
- **Arbitrage Detection** - Calculates best Long/Short pairs with projected daily returns
- **Interactive Dashboard** - Visual charts with 1H, 8H, and annualized rate views
- **Multi-DEX Execution** - Execute trades on multiple exchanges simultaneously

### Pro Features
- **One-Click Arbitrage** - Execute Long+Short positions with a single click
- **Trade Logging** - Track all your executed arbitrage trades
- **Priority Support** - Access to premium features

### Learning Center
- **Interactive FAQ** - Comprehensive guide for beginners
- **AI Assistant** - Powered by Mistral AI to answer crypto questions
- **Risk Education** - Learn about funding rates and arbitrage strategies

---

## Tech Stack

### Frontend
- **Vue.js 3** with Composition API
- **TailwindCSS** for styling
- **Chart.js** for data visualization
- **Reown AppKit** for wallet connection
- **Viem/Wagmi** for blockchain interactions

### Backend
- **Spring Boot 3** (Java 17)
- **PostgreSQL** database
- **Flyway** for migrations
- **Caffeine** for caching
- **Mistral AI** for chat assistant

---

## Quick Start

### Prerequisites
- Docker Desktop
- Java JDK 17+
- Node.js 18+

### 1. Clone & Configure

```bash
git clone https://github.com/your-repo/crypto-arbitrage.git
cd crypto-arbitrage
cp .env.example .env
```

### 2. Edit Environment Variables

```env
# Database
DB_HOST=localhost
DB_PORT=5432
DB_NAME=cryptodb
DB_USERNAME=cryptouser
DB_PASSWORD=cryptopass

# Security
ENCRYPTION_KEY=your-secure-random-key

# Admin Wallet (first admin)
APP_ADMIN_WALLET=0xYourWalletAddress

# Frontend
VITE_WALLETCONNECT_PROJECT_ID=your_project_id
VITE_API_URL=http://localhost:8080/api

# AI Assistant (optional)
MISTRAL_API_KEY=your_mistral_api_key
```

### 3. Start Database

```bash
docker-compose up -d
```

Access phpMyAdmin at `http://localhost:8081`

### 4. Start Backend

```bash
cd backend
./mvnw spring-boot:run
```

API available at `http://localhost:8080`

### 5. Start Frontend

```bash
cd frontend
npm install
npm run dev
```

UI available at `http://localhost:5173`

---

## Project Structure

```
crypto-arbitrage/
├── backend/
│   ├── src/main/java/com/crypto/arbitrage/
│   │   ├── controller/      # REST endpoints
│   │   ├── service/         # Business logic
│   │   ├── repository/      # Data access
│   │   ├── model/           # JPA entities
│   │   ├── scheduler/       # Scheduled tasks
│   │   └── config/          # Configuration
│   └── src/main/resources/
│       ├── application.properties
│       └── db/migration/    # Flyway migrations
├── frontend/
│   ├── src/
│   │   ├── views/           # Page components
│   │   ├── components/      # Reusable components
│   │   ├── services/        # API services
│   │   ├── stores/          # State management
│   │   └── router/          # Vue Router
│   └── public/
└── docker-compose.yml
```

---

## API Endpoints

### Funding Rates
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/funding-rates/latest` | Get all latest rates |
| GET | `/api/funding-rates/ticker/{symbol}` | Get rates for a symbol |
| GET | `/api/funding-rates/dex/{dexName}` | Get rates by DEX |

### Arbitrage
| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/arbitrage/opportunities` | Get current opportunities |
| GET | `/api/arbitrage/historical` | Get historical data |

### Users
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/users/login` | Login/register by wallet |
| GET | `/api/users/profile` | Get user profile |
| POST | `/api/users/upgrade` | Upgrade to Pro |

### Chat (AI Assistant)
| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/chat` | Send message to AI |

---

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `DB_HOST` | Yes | PostgreSQL host |
| `DB_PORT` | Yes | PostgreSQL port |
| `DB_NAME` | Yes | Database name |
| `DB_USERNAME` | Yes | Database user |
| `DB_PASSWORD` | Yes | Database password |
| `ENCRYPTION_KEY` | Yes | Key for encrypting sensitive data |
| `APP_ADMIN_WALLET` | Yes | First admin wallet address |
| `VITE_WALLETCONNECT_PROJECT_ID` | Yes | WalletConnect project ID |
| `VITE_API_URL` | No | Backend API URL |
| `MISTRAL_API_KEY` | No | Mistral AI API key (for chat) |

---

## Caching Strategy

The application uses Caffeine cache to optimize performance:

| Cache | TTL | Purpose |
|-------|-----|---------|
| `arbitrageOpportunities` | 10s | Live arbitrage data |
| `latestFundingRates` | 10s | Current funding rates |
| `historicalArbitrage` | 5min | Historical analytics |

---

## Deployment

### DigitalOcean App Platform

1. Connect your GitHub repository
2. Configure environment variables in App Settings
3. Set build commands:
   - Backend: `cd backend && ./mvnw clean package -DskipTests`
   - Frontend: `cd frontend && npm install && npm run build`

### Docker (Production)

```bash
docker-compose -f docker-compose.prod.yml up -d
```

---

## Security

- Never commit `.env` files
- API keys are encrypted in the database
- Admin routes are protected by wallet-based authentication
- CORS configured for production domains

---

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/amazing-feature`)
3. Commit your changes (`git commit -m 'Add amazing feature'`)
4. Push to the branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request

---

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.

---

## Support

- **Discord**: [Join our community](https://discord.gg/)
- **GitHub Issues**: [Report bugs](https://github.com/cactuszdre/issues)
- **Twitter**: [@cactuszdre](https://twitter.com/cactuszdre)

---

<div align="center">

**Built with 🌵 by [Cactus](https://github.com/cactuszdre)**

</div>
