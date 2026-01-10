package com.crypto.arbitrage.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Configuration for inter-service communication.
 */
@Configuration
public class WebClientConfig {

    @Value("${funding.service.url}")
    private String fundingServiceUrl;

    /**
     * WebClient for calling the Funding Service.
     */
    @Bean
    public WebClient fundingServiceClient() {
        return WebClient.builder()
                .baseUrl(fundingServiceUrl)
                .build();
    }
}
