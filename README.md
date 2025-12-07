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

Edit the `.env` file with your configuration:
```env
# Database Configuration
DB_HOST=localhost
DB_PORT=3306
DB_NAME=cryptodb
DB_USERNAME=cryptouser
DB_PASSWORD=cryptopass
DB_ROOT_PASSWORD=rootpassword

# App Security
ENCRYPTION_KEY=MySuperSecretKey

# WalletConnect Project ID (get one at https://cloud.walletconnect.com)
VITE_WALLETCONNECT_PROJECT_ID=your_project_id_here

# First Admin Wallet (REQUIRED)
# This wallet address will be automatically created as ADMIN with PRO plan on first startup
APP_ADMIN_WALLET=0xYourWalletAddressHere
```

> **⚠️ Important:** 
> - Update the `ENCRYPTION_KEY` to a secure random string for production.
> - Set `APP_ADMIN_WALLET` to your Ethereum wallet address to become the first admin.

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
