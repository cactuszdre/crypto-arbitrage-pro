package com.crypto.funding.repository;

import com.crypto.funding.model.Plan;
import com.crypto.funding.model.Role;
import com.crypto.funding.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    
    /**
     * Find user by wallet address (case-insensitive)
     */
    Optional<User> findByWalletAddressIgnoreCase(String walletAddress);
    
    /**
     * Check if wallet exists
     */
    boolean existsByWalletAddressIgnoreCase(String walletAddress);
    
    /**
     * Find all admins
     */
    List<User> findByRole(Role role);
    
    /**
     * Find all users with active Pro plan
     */
    @Query("SELECT u FROM User u WHERE u.plan = :plan AND u.planExpiresAt > CURRENT_TIMESTAMP")
    List<User> findByActivePlan(Plan plan);
    
    /**
     * Count users by plan
     */
    long countByPlan(Plan plan);
    
    /**
     * Count users by role
     */
    long countByRole(Role role);
}
