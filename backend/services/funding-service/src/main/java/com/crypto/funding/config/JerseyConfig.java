package com.crypto.funding.config;


import com.crypto.funding.controller.DexResource;
import com.crypto.funding.controller.FundingRateResource;
import com.crypto.funding.controller.FundingRatesResource;
import com.crypto.funding.controller.ChatResource;
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
        // Register all JAX-RS resource classes
        register(DexResource.class);
        register(FundingRateResource.class);
        register(FundingRatesResource.class);  // Backwards compatible /api/funding-rates
        // register(ChartResource.class); // Moved to arbitrage-service
        // register(ArbitrageResource.class); // Moved to arbitrage-service
        register(ChatResource.class);
        
        // Register Jackson for JSON serialization
        packages("org.glassfish.jersey.jackson");
    }
}
