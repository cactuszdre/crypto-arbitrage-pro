package com.crypto.auth.business;

import com.crypto.auth.model.Role;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class JwtServiceTest {

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService();
        // Inject the secret key since @Value won't work in unit tests
        ReflectionTestUtils.setField(jwtService, "secretKey", "TestSecretKeyForJwtServiceTests123");
        // Manually call init since @PostConstruct won't run in unit tests
        jwtService.init();
    }

    @Test
    void testGenerateToken() {
        String token = jwtService.generateToken("0x123456", Role.USER);
        
        assertNotNull(token);
        assertFalse(token.isEmpty());
        assertTrue(token.contains(".")); // JWT format: header.payload.signature
    }

    @Test
    void testValidateToken() {
        String token = jwtService.generateToken("0xabc123", Role.ADMIN);
        
        boolean isValid = jwtService.validateToken(token);
        assertTrue(isValid);
    }

    @Test
    void testExtractWalletAddress() {
        String wallet = "0xTestWallet";
        String token = jwtService.generateToken(wallet, Role.USER);
        
        String extracted = jwtService.extractWalletAddress(token);
        assertEquals(wallet, extracted);
    }

    @Test
    void testExtractRole() {
        String token = jwtService.generateToken("0x123", Role.ADMIN);
        
        Role role = jwtService.extractRole(token);
        assertEquals(Role.ADMIN, role);
    }

    @Test
    void testInvalidTokenReturnsFalse() {
        boolean isValid = jwtService.validateToken("invalid.token.here");
        assertFalse(isValid);
    }
}
