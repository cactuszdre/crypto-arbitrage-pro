# Crypto Arbitrage App

A full-stack application to monitor and analyze funding rate arbitrage opportunities across multiple DEXes (Paradex, Lighter, etc.).

## 🚀 Features

*   **Real-time Funding Rates**: Fetches data every 10 seconds.
*   **Arbitrage Opportunities**: Calculates annualized spreads and finding the best Long/Short strategies.
*   **Interactive Dashboard**: Visual charts (1H, 8H, Annualized rates) using Chart.js.
*   **Multi-DEX Support**: Easily extensible architecture (currently supports Paradex & Lighter).
*   **Secure**: Encrypted API keys and environment-based configuration.

## 🛠️ Prerequisites

*   Start Docker Desktop
*   Java JDK 17+
*   Node.js 18+

## 📦 Installation & Setup

### 1. Clone the Repository
```bash
git clone <repository_url>
cd crypto-arbitrage
```

### 2. Configure Environment Variables
Copy the example environment file and configure your secrets:
```bash
cp .env.example .env
```
> **Note:** Update the `ENCRYPTION_KEY` in `.env` to a secure random string for production use.

### 3. Start Infrastructure (Database)
Start MySQL and phpMyAdmin using Docker Compose:
```bash
docker-compose up -d
```
*   **MySQL**: Port 3306
*   **phpMyAdmin**: http://localhost:8081 (User: `root`, Password: see `.env`)

### 4. Start Backend (Spring Boot)
Open a terminal in the `backend` folder:
```bash
cd backend
./mvnw clean spring-boot:run
```
The API will be available at `http://localhost:8080`.

### 5. Start Frontend (Vue.js)
Open a new terminal in the `frontend` folder:
```bash
cd frontend
npm install
npm run dev
```
The UI will be available at `http://localhost:5173`.

## 🔒 Security Notes
*   **Never commit `.env` files.**
*   API keys for DEXes (if private) are encrypted in the database using the `ENCRYPTION_KEY`.

## 🤝 Contributing
1.  Fork the repository
2.  Create a feature branch
3.  Commit your changes
4.  Push to the branch
5.  Open a Pull Request
