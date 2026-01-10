package com.crypto.arbitrage.model;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Data
@NoArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "wallet_address", unique = true, nullable = false, length = 42)
    private String walletAddress;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Role role = Role.USER;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan = Plan.FREE;

    @Column(name = "plan_expires_at")
    private LocalDateTime planExpiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "last_login_at")
    private LocalDateTime lastLoginAt;

    @Column(name = "nonce")
    private String nonce;  // For signature verification during login

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public User(String walletAddress) {
        this.walletAddress = walletAddress.toLowerCase();
        this.role = Role.USER;
        this.plan = Plan.FREE;
    }

    public User(String walletAddress, Role role) {
        this.walletAddress = walletAddress.toLowerCase();
        this.role = role;
        this.plan = Plan.FREE;
    }

    /**
     * Check if user has an active Pro subscription
     */
    public boolean hasActivePro() {
        if (plan != Plan.PRO) return false;
        if (planExpiresAt == null) return false;
        return planExpiresAt.isAfter(LocalDateTime.now());
    }

    /**
     * Check if user is admin
     */
    public boolean isAdmin() {
        return role == Role.ADMIN;
    }

    /**
     * Normalize wallet address to lowercase
     */
    public void setWalletAddress(String walletAddress) {
        this.walletAddress = walletAddress != null ? walletAddress.toLowerCase() : null;
    }
}
