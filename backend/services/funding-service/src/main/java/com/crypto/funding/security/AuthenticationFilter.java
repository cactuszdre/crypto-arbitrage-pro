package com.crypto.funding.security;

import com.crypto.funding.business.JwtService;
import com.crypto.funding.model.Role;
import jakarta.annotation.Priority;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.SecurityContext;
import jakarta.ws.rs.ext.Provider;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.security.Principal;

@Provider
@Component
@Priority(Priorities.AUTHENTICATION)
@RequiredArgsConstructor
public class AuthenticationFilter implements ContainerRequestFilter {

    private final JwtService jwtService;

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        String authHeader = requestContext.getHeaderString(HttpHeaders.AUTHORIZATION);

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            if (jwtService.validateToken(token)) {
                String wallet = jwtService.extractWalletAddress(token);
                Role role = jwtService.extractRole(token);

                final SecurityContext currentSecurityContext = requestContext.getSecurityContext();
                requestContext.setSecurityContext(new SecurityContext() {

                    @Override
                    public Principal getUserPrincipal() {
                        return () -> wallet;
                    }

                    @Override
                    public boolean isUserInRole(String roleToCheck) {
                        return role.name().equalsIgnoreCase(roleToCheck);
                    }

                    @Override
                    public boolean isSecure() {
                        return currentSecurityContext.isSecure();
                    }

                    @Override
                    public String getAuthenticationScheme() {
                        return "Bearer";
                    }
                });
            }
        }
    }
}
