package com.crypto.arbitrage.security;

import com.crypto.arbitrage.model.User;
import com.crypto.arbitrage.repository.UserRepository;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Optional;

/**
 * Service for JWT token generation and validation.
 * Creates secure tokens containing wallet address and role information.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final UserRepository userRepository;

    @Value("${jwt.secret:your-256-bit-secret-key-change-this-in-production-please}")
    private String jwtSecret;

    @Value("${jwt.expiration-hours:24}")
    private int expirationHours;

    private SecretKey secretKey;

    @PostConstruct
    public void init() {
        // Ensure the secret is at least 256 bits (32 bytes) for HS256
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        if (keyBytes.length < 32) {
            // Pad the key if too short (only for development)
            byte[] paddedKey = new byte[32];
            System.arraycopy(keyBytes, 0, paddedKey, 0, Math.min(keyBytes.length, 32));
            keyBytes = paddedKey;
        }
        this.secretKey = Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * Generate a JWT token for an authenticated user.
     *
     * @param walletAddress The verified wallet address
     * @return JWT token string
     */
    public String generateToken(String walletAddress) {
        User user = userRepository.findByWalletAddressIgnoreCase(walletAddress)
                .orElseThrow(() -> new IllegalArgumentException("User not found: " + walletAddress));

        Date now = new Date();
        Date expiry = new Date(now.getTime() + (expirationHours * 60L * 60L * 1000L));

        return Jwts.builder()
                .subject(walletAddress.toLowerCase())
                .claim("role", user.getRole().name())
                .claim("userId", user.getId())
                .issuedAt(now)
                .expiration(expiry)
                .signWith(secretKey)
                .compact();
    }

    /**
     * Validate a JWT token and extract claims.
     *
     * @param token JWT token
     * @return Claims if valid, empty if invalid
     */
    public Optional<Claims> validateToken(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(secretKey)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(claims);
        } catch (ExpiredJwtException e) {
            log.warn("JWT token expired");
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
        }
        return Optional.empty();
    }

    /**
     * Extract wallet address from a valid token.
     */
    public Optional<String> getWalletAddress(String token) {
        return validateToken(token).map(Claims::getSubject);
    }

    /**
     * Extract role from a valid token.
     */
    public Optional<String> getRole(String token) {
        return validateToken(token).map(claims -> claims.get("role", String.class));
    }
}
