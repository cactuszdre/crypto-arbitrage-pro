package com.crypto.auth.config;

import com.crypto.auth.controller.AuthResource;
import com.crypto.auth.exception.JerseyExceptionMapper;
import jakarta.ws.rs.ApplicationPath;
import org.glassfish.jersey.server.ResourceConfig;
import org.springframework.stereotype.Component;

@Component
@ApplicationPath("/api")
public class JerseyConfig extends ResourceConfig {
    public JerseyConfig() {
        register(AuthResource.class);
        register(JerseyExceptionMapper.class);
        packages("org.glassfish.jersey.jackson");
    }
}
