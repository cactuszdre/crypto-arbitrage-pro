package com.crypto.auth.dto;

import lombok.Data;

@Data
public class LoginRequest {
    private String walletAddress;
    private String signature;
}
