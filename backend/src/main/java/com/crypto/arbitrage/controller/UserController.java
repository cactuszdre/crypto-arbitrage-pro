package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.model.Role;
import com.crypto.arbitrage.model.User;
import com.crypto.arbitrage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * User management controller.
 * 
 * Security is now handled by Spring Security + JWT:
 * - Admin endpoints require ADMIN role (enforced by @PreAuthorize)
 * - JWT token contains verified wallet address (no spoofing possible)
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserRepository userRepository;

    /**
     * Get the currently authenticated wallet address from the JWT token.
     */
    private String getCurrentWallet() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null ? (String) auth.getPrincipal() : null;
    }

    /**
     * Get all users (admin only)
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllUsers() {
        List<Map<String, Object>> users = userRepository.findAll()
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(users);
    }

    /**
     * Get all admins (admin only)
     */
    @GetMapping("/admins")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Map<String, Object>>> getAllAdmins() {
        List<Map<String, Object>> admins = userRepository.findByRole(Role.ADMIN)
                .stream()
                .map(this::toDto)
                .collect(Collectors.toList());
        return ResponseEntity.ok(admins);
    }

    /**
     * Get user by wallet address (public for login checks)
     */
    @GetMapping("/wallet/{walletAddress}")
    public ResponseEntity<?> getUserByWallet(@PathVariable String walletAddress) {
        return userRepository.findByWalletAddressIgnoreCase(walletAddress)
                .map(user -> ResponseEntity.ok(toDto(user)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Register or login user with wallet (public)
     * Creates new user if doesn't exist, returns existing user otherwise
     * Note: This is a legacy endpoint. New authentication should use /api/auth/*
     */
    @PostMapping("/wallet/{walletAddress}/login")
    public ResponseEntity<?> loginWithWallet(@PathVariable String walletAddress) {
        User user = userRepository.findByWalletAddressIgnoreCase(walletAddress)
                .orElseGet(() -> {
                    User newUser = new User(walletAddress);
                    return userRepository.save(newUser);
                });
        
        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);
        
        return ResponseEntity.ok(toDto(user));
    }

    /**
     * Check if wallet is admin (public)
     */
    @GetMapping("/wallet/{walletAddress}/is-admin")
    public ResponseEntity<Map<String, Boolean>> isAdmin(@PathVariable String walletAddress) {
        boolean isAdmin = userRepository.findByWalletAddressIgnoreCase(walletAddress)
                .map(User::isAdmin)
                .orElse(false);
        
        Map<String, Boolean> response = new HashMap<>();
        response.put("isAdmin", isAdmin);
        return ResponseEntity.ok(response);
    }

    /**
     * Add a new admin by wallet address (admin only)
     * Protected by JWT + ADMIN role - no spoofing possible!
     */
    @PostMapping("/admins")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> addAdmin(@RequestBody Map<String, String> request) {
        String walletAddress = request.get("walletAddress");
        if (walletAddress == null || walletAddress.trim().isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "Wallet address is required"));
        }

        // Find or create user
        User user = userRepository.findByWalletAddressIgnoreCase(walletAddress)
                .orElseGet(() -> {
                    User newUser = new User(walletAddress);
                    return userRepository.save(newUser);
                });

        // Promote to admin
        user.setRole(Role.ADMIN);
        userRepository.save(user);

        return ResponseEntity.ok(toDto(user));
    }

    /**
     * Remove admin role (demote to user) - admin only
     * Cannot remove your own admin role
     */
    @DeleteMapping("/admins/{walletAddress}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> removeAdmin(@PathVariable String walletAddress) {
        String callerWallet = getCurrentWallet();
        
        // Prevent admin from removing their own permissions
        if (callerWallet != null && callerWallet.equalsIgnoreCase(walletAddress)) {
            return ResponseEntity.badRequest().body(Map.of("error", "You cannot remove your own admin permissions"));
        }
        
        return userRepository.findByWalletAddressIgnoreCase(walletAddress)
                .map(user -> {
                    user.setRole(Role.USER);
                    userRepository.save(user);
                    return ResponseEntity.ok(toDto(user));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Delete user completely (admin only)
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteUser(@PathVariable Long id) {
        return userRepository.findById(id)
                .map(user -> {
                    userRepository.delete(user);
                    return ResponseEntity.ok(Map.of("message", "User deleted successfully"));
                })
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Convert User to DTO (hide sensitive data)
     */
    private Map<String, Object> toDto(User user) {
        Map<String, Object> dto = new HashMap<>();
        dto.put("id", user.getId());
        dto.put("walletAddress", user.getWalletAddress());
        dto.put("role", user.getRole().name());
        dto.put("plan", user.getPlan().name());
        dto.put("planExpiresAt", user.getPlanExpiresAt());
        dto.put("createdAt", user.getCreatedAt());
        dto.put("lastLoginAt", user.getLastLoginAt());
        return dto;
    }
}
