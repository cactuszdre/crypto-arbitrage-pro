package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.model.User;
import com.crypto.arbitrage.model.Role;
import com.crypto.arbitrage.model.Plan;
import com.crypto.arbitrage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    /**
     * Get or create nonce for wallet authentication
     * Frontend calls this before signing
     */
    @GetMapping("/nonce/{walletAddress}")
    public ResponseEntity<?> getNonce(@PathVariable String walletAddress) {
        String normalizedWallet = walletAddress.toLowerCase();
        
        User user = userRepository.findByWalletAddressIgnoreCase(normalizedWallet)
                .orElseGet(() -> {
                    // Auto-create user on first connection
                    User newUser = new User(normalizedWallet);
                    return userRepository.save(newUser);
                });
        
        // Generate new nonce for signing
        String nonce = UUID.randomUUID().toString();
        user.setNonce(nonce);
        userRepository.save(user);
        
        return ResponseEntity.ok(Map.of(
                "nonce", nonce,
                "message", "Sign this message to authenticate: " + nonce
        ));
    }

    /**
     * Verify wallet signature and authenticate
     * TODO: Implement actual signature verification
     */
    @PostMapping("/verify")
    public ResponseEntity<?> verifySignature(@RequestBody Map<String, String> request) {
        String walletAddress = request.get("walletAddress");
        String signature = request.get("signature");
        
        if (walletAddress == null || signature == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing walletAddress or signature"));
        }
        
        return userRepository.findByWalletAddressIgnoreCase(walletAddress)
                .map(user -> {
                    // TODO: Verify signature matches the nonce
                    // For now, we trust the signature (implement web3j verification)
                    
                    // Update last login
                    user.setLastLoginAt(LocalDateTime.now());
                    user.setNonce(null); // Clear nonce after use
                    userRepository.save(user);
                    
                    return ResponseEntity.ok(Map.of(
                            "success", true,
                            "user", Map.of(
                                    "walletAddress", user.getWalletAddress(),
                                    "role", user.getRole().name(),
                                    "plan", user.getPlan().name(),
                                    "isPro", user.hasActivePro(),
                                    "isAdmin", user.isAdmin()
                            )
                    ));
                })
                .orElse(ResponseEntity.status(401).body(Map.of("error", "User not found")));
    }

    /**
     * Get current user info
     */
    @GetMapping("/me/{walletAddress}")
    public ResponseEntity<?> getCurrentUser(@PathVariable String walletAddress) {
        return userRepository.findByWalletAddressIgnoreCase(walletAddress)
                .map(user -> ResponseEntity.ok(Map.of(
                        "walletAddress", user.getWalletAddress(),
                        "role", user.getRole().name(),
                        "plan", user.getPlan().name(),
                        "isPro", user.hasActivePro(),
                        "isAdmin", user.isAdmin(),
                        "planExpiresAt", user.getPlanExpiresAt() != null ? user.getPlanExpiresAt().toString() : null
                )))
                .orElse(ResponseEntity.notFound().build());
    }
}
