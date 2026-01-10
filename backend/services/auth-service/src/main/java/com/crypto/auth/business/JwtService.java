package com.crypto.auth.business;

import com.crypto.auth.model.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.security.Key;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

@Service
public class JwtService {

    @Value("${app.encryption.key:MySuperSecretKeyForDevelopmentMustBeChangedInProd}")
    private String secretKey;

    private Key key;

    // Token validity: 24 hours
    private static final long EXIPRATION_TIME_MS = 1000 * 60 * 60 * 24; 

    @PostConstruct
    public void init() {
        // Ensure key is long enough for HS256
        String usableKey = secretKey;
        if (usableKey.length() < 32) {
            usableKey = String.format("%-32s", usableKey).replace(' ', 'x');
        }
        this.key = Keys.hmacShaKeyFor(usableKey.getBytes());
    }

    public String extractWalletAddress(String token) {
        return extractClaim(token, Claims::getSubject);
    }

    public Role extractRole(String token) {
        String roleStr = extractClaim(token, claims -> claims.get("role", String.class));
        try {
            return Role.valueOf(roleStr);
        } catch (Exception e) {
            return Role.USER;
        }
    }

    public <T> T extractClaim(String token, Function<Claims, T> claimsResolver) {
        final Claims claims = extractAllClaims(token);
        return claimsResolver.apply(claims);
    }

    public String generateToken(String walletAddress, Role role) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("role", role.name());
        return createToken(claims, walletAddress);
    }

    private String createToken(Map<String, Object> claims, String subject) {
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(subject)
                .setIssuedAt(new Date(System.currentTimeMillis()))
                .setExpiration(new Date(System.currentTimeMillis() + EXIPRATION_TIME_MS))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    public boolean isTokenValid(String token, String walletAddress) {
        final String username = extractWalletAddress(token);
        return (username.equalsIgnoreCase(walletAddress) && !isTokenExpired(token));
    }
    
    public boolean validateToken(String token) {
        try {
            extractAllClaims(token);
            return !isTokenExpired(token);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isTokenExpired(String token) {
        return extractExpiration(token).before(new Date());
    }

    private Date extractExpiration(String token) {
        return extractClaim(token, Claims::getExpiration);
    }

    private Claims extractAllClaims(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build().parseClaimsJws(token).getBody();
    }
}
