package com.crypto.arbitrage.config;

import com.crypto.arbitrage.security.JwtAuthenticationFilter;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.Arrays;
import java.util.List;

/**
 * Security configuration with JWT-based authentication.
 * 
 * Public endpoints (no auth required):
 * - GET /api/* - All read operations for public data
 * - POST /api/auth/* - Authentication endpoints
 * - /actuator/health - Health checks
 * 
 * Protected endpoints (require valid JWT):
 * - POST/PUT/DELETE on most endpoints
 * 
 * Admin endpoints (require ADMIN role):
 * - /api/users/admins - Admin management
 * - /api/dex/sync-markets - Market sync
 * - /api/charts/admin/* - Admin chart operations
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(List.of("*")); // Allow all origins with patterns
        configuration.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setExposedHeaders(List.of("Authorization")); // Expose auth header
        configuration.setAllowCredentials(true);
        
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .cors(cors -> cors.configurationSource(corsConfigurationSource()))
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // ============ PUBLIC ENDPOINTS (no auth) ============
                
                // CORS preflight
                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                
                // Health checks (required for deployment)
                .requestMatchers("/actuator/**").permitAll()
                
                // Authentication endpoints
                .requestMatchers("/api/auth/**").permitAll()
                
                // Public read-only data endpoints
                .requestMatchers(HttpMethod.GET, "/api/funding-rates/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/arbitrage/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/charts/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/dex/**").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/markets/**").permitAll()
                
                // User check by wallet (needed for initial login check)
                .requestMatchers(HttpMethod.GET, "/api/users/wallet/**").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/users/wallet/*/login").permitAll()
                
                // ============ ADMIN ENDPOINTS (require ADMIN role) ============
                
                // User/Admin management
                .requestMatchers("/api/users/admins/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.GET, "/api/users").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/users/**").hasRole("ADMIN")
                
                // DEX management
                .requestMatchers(HttpMethod.POST, "/api/dex").hasRole("ADMIN")
                .requestMatchers(HttpMethod.POST, "/api/dex/sync-markets").hasRole("ADMIN")
                .requestMatchers(HttpMethod.PUT, "/api/dex/**").hasRole("ADMIN")
                .requestMatchers(HttpMethod.DELETE, "/api/dex/**").hasRole("ADMIN")
                
                // Admin chart operations
                .requestMatchers("/api/charts/admin/**").hasRole("ADMIN")
                
                // ============ AUTHENTICATED ENDPOINTS (require valid JWT) ============
                
                // All other POST/PUT/DELETE require authentication
                .requestMatchers(HttpMethod.POST, "/**").authenticated()
                .requestMatchers(HttpMethod.PUT, "/**").authenticated()
                .requestMatchers(HttpMethod.DELETE, "/**").authenticated()
                
                // All other requests allowed (catch remaining GETs)
                .anyRequest().permitAll()
            )
            // Add JWT filter before the standard auth filter
            .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        
        return http.build();
    }
}
