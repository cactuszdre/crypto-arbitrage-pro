package com.crypto.arbitrage.config;

import com.crypto.arbitrage.model.Plan;
import com.crypto.arbitrage.model.Role;
import com.crypto.arbitrage.model.User;
import com.crypto.arbitrage.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@RequiredArgsConstructor
public class DataInitializer {

    private final UserRepository userRepository;

    @Value("${app.admin.wallet:}")
    private String adminWallet;

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            System.out.println("=== DataInitializer ===");
            System.out.println("APP_ADMIN_WALLET value: '" + adminWallet + "'");
            
            // Create admin from environment variable if set
            if (adminWallet != null && !adminWallet.isEmpty()) {
                System.out.println("Admin wallet is configured, checking if exists...");
                if (userRepository.findByWalletAddressIgnoreCase(adminWallet).isEmpty()) {
                    User admin = new User(adminWallet, Role.ADMIN);
                    admin.setPlan(Plan.PRO);
                    userRepository.save(admin);
                    System.out.println("✅ Admin user created with wallet: " + adminWallet);
                } else {
                    System.out.println("Admin already exists in database");
                }
            } else {
                System.out.println("⚠️ APP_ADMIN_WALLET is not set or empty!");
            }
        };
    }
}
