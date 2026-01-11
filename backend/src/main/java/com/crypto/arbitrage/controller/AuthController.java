package com.crypto.arbitrage.controller;

import com.crypto.arbitrage.model.Role;
import com.crypto.arbitrage.model.User;
import com.crypto.arbitrage.repository.UserRepository;
import com.crypto.arbitrage.security.JwtService;
import com.crypto.arbitrage.security.WalletSignatureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * Authentication controller for wallet-based login.
 * Implements secure challenge-response authentication:
 * 1. Client requests a nonce
 * 2. User signs the nonce with their wallet
 * 3. Server verifies signature and issues JWT
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final WalletSignatureService signatureService;
    private final JwtService jwtService;
    private final UserRepository userRepository;

    /**
     * Step 1: Request a nonce to sign.
     * The client must sign this nonce with their wallet to prove ownership.
     *
     * @param walletAddress The wallet address requesting authentication
     * @return Nonce and message to sign
     */
    @GetMapping("/nonce/{walletAddress}")
    public ResponseEntity<Map<String, String>> getNonce(@PathVariable String walletAddress) {
        if (walletAddress == null || walletAddress.length() != 42 || !walletAddress.startsWith("0x")) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid wallet address format"));
        }

        String nonce = signatureService.generateNonce(walletAddress);
        String message = signatureService.createSignMessage(nonce);

        return ResponseEntity.ok(Map.of(
            "nonce", nonce,
            "message", message
        ));
    }

    /**
     * Step 2: Verify signature and issue JWT.
     * The client sends their signature of the nonce.
     * If valid, a JWT token is issued for subsequent requests.
     *
     * @param request Contains walletAddress, signature, and nonce
     * @return JWT token if signature is valid
     */
    @PostMapping("/verify")
    public ResponseEntity<Map<String, Object>> verifySignature(@RequestBody AuthRequest request) {
        // Validate request
        if (request.walletAddress() == null || request.signature() == null || request.nonce() == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields"));
        }

        // Verify the signature
        boolean isValid = signatureService.verifySignature(
            request.walletAddress(),
            request.signature(),
            request.nonce()
        );

        if (!isValid) {
            log.warn("Failed authentication attempt for wallet: {}", request.walletAddress());
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid signature"));
        }

        // Get or create user
        User user = userRepository.findByWalletAddressIgnoreCase(request.walletAddress())
            .orElseGet(() -> {
                User newUser = new User(request.walletAddress());
                return userRepository.save(newUser);
            });

        // Update last login
        user.setLastLoginAt(LocalDateTime.now());
        userRepository.save(user);

        // Generate JWT token
        String token = jwtService.generateToken(request.walletAddress());

        log.info("User authenticated: {}", request.walletAddress());

        return ResponseEntity.ok(Map.of(
            "token", token,
            "walletAddress", user.getWalletAddress(),
            "role", user.getRole().name(),
            "isAdmin", user.isAdmin()
        ));
    }

    /**
     * Refresh token endpoint.
     * Allows users to get a new token before the old one expires.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Map<String, Object>> refreshToken(
            @RequestHeader("Authorization") String authHeader) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "No valid token provided"));
        }

        String token = authHeader.substring(7);
        
        return jwtService.getWalletAddress(token)
            .map(walletAddress -> {
                String newToken = jwtService.generateToken(walletAddress);
                User user = userRepository.findByWalletAddressIgnoreCase(walletAddress)
                    .orElseThrow();
                
                return ResponseEntity.ok(Map.<String, Object>of(
                    "token", newToken,
                    "walletAddress", user.getWalletAddress(),
                    "role", user.getRole().name()
                ));
            })
            .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Invalid or expired token")));
    }

    /**
     * Verify current token is valid (for frontend session checks).
     */
    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> getCurrentUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {
        
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("authenticated", false));
        }

        String token = authHeader.substring(7);
        
        return jwtService.getWalletAddress(token)
            .flatMap(userRepository::findByWalletAddressIgnoreCase)
            .map(user -> ResponseEntity.ok(Map.<String, Object>of(
                "authenticated", true,
                "walletAddress", user.getWalletAddress(),
                "role", user.getRole().name(),
                "isAdmin", user.isAdmin(),
                "plan", user.getPlan().name()
            )))
            .orElse(ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("authenticated", false)));
    }

    /**
     * Request record for verify endpoint.
     */
    public record AuthRequest(String walletAddress, String signature, String nonce) {}
}
