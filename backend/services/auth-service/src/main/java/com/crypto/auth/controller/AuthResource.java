package com.crypto.auth.controller;

import com.crypto.auth.business.Web3AuthService;
import com.crypto.auth.dto.AuthResponse;
import com.crypto.auth.dto.LoginRequest;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Path("/auth")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
public class AuthResource {

    private final Web3AuthService authService;

    @GET
    @Path("/nonce/{walletAddress}")
    public Map<String, String> getNonce(@PathParam("walletAddress") String walletAddress) {
        String nonce = authService.getNonce(walletAddress);
        return Map.of("nonce", nonce);
    }

    @POST
    @Path("/login")
    public AuthResponse login(LoginRequest request) {
        return authService.verifyAndLogin(request.getWalletAddress(), request.getSignature());
    }
}
