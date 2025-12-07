package com.crypto.arbitrage.config;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Application context initializer that loads .env file BEFORE Spring context starts.
 * This ensures environment variables are available for property resolution.
 * 
 * Security notes:
 * - The .env file should NEVER be committed to git (add to .gitignore)
 * - Values are only loaded into system properties, not exposed via API
 * - Supports both project root and backend directory locations
 */
@Component
public class DotenvInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final String[] ENV_FILE_LOCATIONS = {
        ".env",           // Current directory (backend/)
        "../.env",        // Parent directory (project root)
        "../../.env"      // Two levels up (in case of nested execution)
    };

    @Override
    public void initialize(ConfigurableApplicationContext applicationContext) {
        loadEnvFile();
    }

    public static void loadEnvFile() {
        for (String location : ENV_FILE_LOCATIONS) {
            Path envPath = Paths.get(location).toAbsolutePath().normalize();
            if (Files.exists(envPath)) {
                System.out.println("📁 Loading .env from: " + envPath);
                try {
                    int count = 0;
                    for (String line : Files.readAllLines(envPath)) {
                        // Skip comments and empty lines
                        String trimmed = line.trim();
                        if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                            continue;
                        }
                        
                        // Parse KEY=VALUE
                        int equalsIndex = trimmed.indexOf('=');
                        if (equalsIndex > 0) {
                            String key = trimmed.substring(0, equalsIndex).trim();
                            String value = trimmed.substring(equalsIndex + 1).trim();
                            
                            // Remove surrounding quotes if present
                            if ((value.startsWith("\"") && value.endsWith("\"")) ||
                                (value.startsWith("'") && value.endsWith("'"))) {
                                value = value.substring(1, value.length() - 1);
                            }
                            
                            // Only set if not already defined (env vars take precedence)
                            if (System.getProperty(key) == null && System.getenv(key) == null) {
                                System.setProperty(key, value);
                                count++;
                            }
                        }
                    }
                    
                    System.out.println("✅ Loaded " + count + " environment variables from .env");
                    return; // Stop after first successful load
                    
                } catch (IOException e) {
                    System.err.println("⚠️ Error reading .env file: " + e.getMessage());
                }
            }
        }
        
        System.out.println("⚠️ No .env file found in expected locations");
    }
}
