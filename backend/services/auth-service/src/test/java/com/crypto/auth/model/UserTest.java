package com.crypto.auth.model;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UserTest {

    @Test
    void testUserCreation() {
        User user = new User("0xAbC123");
        
        assertEquals("0xabc123", user.getWalletAddress()); // Should be lowercase
        assertEquals(Role.USER, user.getRole());
        assertEquals(Plan.FREE, user.getPlan());
    }

    @Test
    void testUserWithRole() {
        User user = new User("0xDEF456", Role.ADMIN);
        
        assertEquals("0xdef456", user.getWalletAddress());
        assertEquals(Role.ADMIN, user.getRole());
        assertTrue(user.isAdmin());
    }

    @Test
    void testWalletAddressNormalization() {
        User user = new User();
        user.setWalletAddress("0xABCDEF");
        
        assertEquals("0xabcdef", user.getWalletAddress());
    }

    @Test
    void testIsAdminFalseForUser() {
        User user = new User("0x123");
        assertFalse(user.isAdmin());
    }

    @Test
    void testHasActiveProWithNoPlan() {
        User user = new User("0x123");
        assertFalse(user.hasActivePro());
    }
}
