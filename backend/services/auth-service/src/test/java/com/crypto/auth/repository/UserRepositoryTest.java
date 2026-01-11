package com.crypto.auth.repository;

import com.crypto.auth.model.Plan;
import com.crypto.auth.model.Role;
import com.crypto.auth.model.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void testSaveAndFindByWallet() {
        User user = new User("0xABC123");
        userRepository.save(user);

        Optional<User> found = userRepository.findByWalletAddressIgnoreCase("0xabc123");
        
        assertTrue(found.isPresent());
        assertEquals("0xabc123", found.get().getWalletAddress());
    }

    @Test
    void testFindByWalletCaseInsensitive() {
        User user = new User("0xdef456");
        userRepository.save(user);

        Optional<User> found = userRepository.findByWalletAddressIgnoreCase("0xDEF456");
        
        assertTrue(found.isPresent());
    }

    @Test
    void testExistsByWallet() {
        User user = new User("0x999");
        userRepository.save(user);

        assertTrue(userRepository.existsByWalletAddressIgnoreCase("0x999"));
        assertFalse(userRepository.existsByWalletAddressIgnoreCase("0x888"));
    }

    @Test
    void testFindByRole() {
        User admin = new User("0xadmin", Role.ADMIN);
        User user1 = new User("0xuser1");
        User user2 = new User("0xuser2");
        
        userRepository.save(admin);
        userRepository.save(user1);
        userRepository.save(user2);

        List<User> admins = userRepository.findByRole(Role.ADMIN);
        List<User> users = userRepository.findByRole(Role.USER);

        assertEquals(1, admins.size());
        assertEquals(2, users.size());
    }

    @Test
    void testCountByPlan() {
        User pro = new User("0xpro");
        pro.setPlan(Plan.PRO);
        
        User free1 = new User("0xfree1");
        User free2 = new User("0xfree2");
        
        userRepository.save(pro);
        userRepository.save(free1);
        userRepository.save(free2);

        assertEquals(1, userRepository.countByPlan(Plan.PRO));
        assertEquals(2, userRepository.countByPlan(Plan.FREE));
    }
}
