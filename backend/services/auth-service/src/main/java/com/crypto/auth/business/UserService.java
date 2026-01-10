package com.crypto.auth.business;

import com.crypto.auth.exception.BusinessException;
import com.crypto.auth.exception.ResourceNotFoundException;
import com.crypto.auth.model.Plan;
import com.crypto.auth.model.Role;
import com.crypto.auth.model.User;
import com.crypto.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserService {

    private final UserRepository userRepository;

    @Value("${app.admin.wallet}")
    private String adminWallet;

    public List<User> getAllUsers() {
        return userRepository.findAll();
    }

    public List<User> getAdmins() {
        return userRepository.findByRole(Role.ADMIN);
    }

    public User getUserByWallet(String walletAddress) {
        return userRepository.findByWalletAddressIgnoreCase(walletAddress)
                .orElseThrow(() -> new ResourceNotFoundException("User", "wallet", walletAddress));
    }

    @Transactional
    public User addAdmin(String walletAddress) {
        String normalized = walletAddress.toLowerCase();
        User user = userRepository.findByWalletAddressIgnoreCase(normalized)
                .orElseGet(() -> new User(normalized));
        
        user.setRole(Role.ADMIN);
        return userRepository.save(user);
    }

    @Transactional
    public void removeAdmin(String walletAddress) {
        if (adminWallet != null && walletAddress.equalsIgnoreCase(adminWallet)) {
            throw new BusinessException("ADMIN_PROTECTED", "Cannot remove the Super Admin configured in environment.");
        }
        
        User user = getUserByWallet(walletAddress);
        user.setRole(Role.USER);
        userRepository.save(user);
    }

    @Transactional
    public User updateRole(Long userId, Role role) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        // Protect super admin de-promotion
        if (adminWallet != null && user.getWalletAddress().equalsIgnoreCase(adminWallet) && role != Role.ADMIN) {
             throw new BusinessException("ADMIN_PROTECTED", "Cannot demote the Super Admin.");
        }
        
        user.setRole(role);
        return userRepository.save(user);
    }

    @Transactional
    public User updatePlan(Long userId, Plan plan) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userId));
        
        user.setPlan(plan);
        if (plan == Plan.PRO) {
            // Give 30 days
            user.setPlanExpiresAt(LocalDateTime.now().plusDays(30)); 
        } else {
            user.setPlanExpiresAt(null);
        }
        return userRepository.save(user);
    }

    @Transactional
    public void updateLastLogin(String walletAddress) {
        userRepository.findByWalletAddressIgnoreCase(walletAddress)
                .ifPresent(user -> {
                    user.setLastLoginAt(LocalDateTime.now());
                    userRepository.save(user);
                });
    }
}
