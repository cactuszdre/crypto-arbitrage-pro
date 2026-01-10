package com.crypto.auth.dto;

import com.crypto.auth.model.Role;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AuthResponse {
    private String token;
    private Role role;
    private String walletAddress;
}
