package com.crypto.auth.business;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService();

    @Test
    void testGenerateToken() {
        String token = jwtService.generateToken("0x123456", "USER");
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains(".")); // JWT format: header.payload.signature
    }

    @Test
    void testValidateToken() {
        String token = jwtService.generateToken("0xabc123", "ADMIN");
        
        boolean isValid = jwtService.validateToken(token);
        assertTrue(isValid);
    }

    @Test
    void testExtractWallet() {
        String wallet = "0xTestWallet";
        String token = jwtService.generateToken(wallet, "USER");
        
        String extracted = jwtService.extractWallet(token);
        assertEquals(wallet, extracted);
    }

    @Test
    void testExtractRole() {
        String token = jwtService.generateToken("0x123", "ADMIN");
        
        String role = jwtService.extractRole(token);
        assertEquals("ADMIN", role);
    }

    @Test
    void testInvalidTokenReturnsFalse() {
        boolean isValid = jwtService.validateToken("invalid.token.here");
        assertFalse(isValid);
    }
}
