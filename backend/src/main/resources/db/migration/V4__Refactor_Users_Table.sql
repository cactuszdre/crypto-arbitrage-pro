-- Migration V4: Refactor users table for wallet-based authentication
-- This migration drops the old users table and creates a new one with wallet support

-- Drop existing users table if exists (WARNING: This will delete existing user data!)
DROP TABLE IF EXISTS users;

-- Create new users table with wallet-based authentication
CREATE TABLE users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    wallet_address VARCHAR(42) NOT NULL UNIQUE,
    role ENUM('USER', 'ADMIN') NOT NULL DEFAULT 'USER',
    plan ENUM('FREE', 'PRO') NOT NULL DEFAULT 'FREE',
    plan_expires_at DATETIME NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at DATETIME NULL,
    nonce VARCHAR(255) NULL,
    
    INDEX idx_wallet (wallet_address),
    INDEX idx_role (role),
    INDEX idx_plan (plan),
    INDEX idx_plan_expires (plan_expires_at)
);

-- Optional: Insert a default admin wallet (uncomment and replace with your wallet)
-- INSERT INTO users (wallet_address, role, plan) VALUES 
-- ('0xYOUR_ADMIN_WALLET_ADDRESS_HERE', 'ADMIN', 'PRO');
