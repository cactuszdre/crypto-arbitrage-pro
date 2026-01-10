package com.crypto.arbitrage.config;

import com.crypto.arbitrage.controller.ArbitrageResource;
import com.crypto.arbitrage.controller.ChartResource;
import jakarta.ws.rs.ApplicationPath;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

/**
 * Jersey configuration for JAX-RS endpoints.
 * All REST endpoints are exposed under /api path.
 */
@Component
@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {

    public JerseyConfig() {
        // Register JAX-RS resource classes
        register(ArbitrageResource.class);
        register(ChartResource.class);
        register(com.crypto.arbitrage.exception.JerseyExceptionMapper.class);
        register(com.crypto.arbitrage.security.AuthenticationFilter.class);
        register(org.glassfish.jersey.server.filter.RolesAllowedDynamicFeature.class);
        
        // Register Jackson for JSON serialization
        packages("org.glassfish.jersey.jackson");
    }
}
