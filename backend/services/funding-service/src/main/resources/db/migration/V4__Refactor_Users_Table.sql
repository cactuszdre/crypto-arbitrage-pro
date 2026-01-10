-- Migration V4: Refactor users table for wallet-based authentication
-- This migration drops the old users table and creates a new one with wallet support

-- Drop existing users table if exists (WARNING: This will delete existing user data!)
DROP TABLE IF EXISTS users;

-- Create new users table with wallet-based authentication
CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    wallet_address VARCHAR(42) NOT NULL UNIQUE,
    role VARCHAR(10) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'ADMIN')),
    plan VARCHAR(10) NOT NULL DEFAULT 'FREE' CHECK (plan IN ('FREE', 'PRO')),
    plan_expires_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_login_at TIMESTAMP NULL,
    nonce VARCHAR(255) NULL
);

CREATE INDEX idx_users_wallet ON users (wallet_address);
CREATE INDEX idx_users_role ON users (role);
CREATE INDEX idx_users_plan ON users (plan);
CREATE INDEX idx_users_plan_expires ON users (plan_expires_at);

-- Optional: Insert a default admin wallet (uncomment and replace with your wallet)
-- INSERT INTO users (wallet_address, role, plan) VALUES 
-- ('0xYOUR_ADMIN_WALLET_ADDRESS_HERE', 'ADMIN', 'PRO');
