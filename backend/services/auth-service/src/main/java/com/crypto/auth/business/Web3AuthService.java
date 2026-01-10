package com.crypto.auth.business;

import java.nio.charset.StandardCharsets;

import com.crypto.auth.dto.AuthResponse;
import com.crypto.auth.exception.BusinessException;
import com.crypto.auth.exception.ResourceNotFoundException;
import com.crypto.auth.model.Plan;
import com.crypto.auth.model.Role;
import com.crypto.auth.model.User;
import com.crypto.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.security.SignatureException;
import java.util.Arrays;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class Web3AuthService {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @Value("${app.admin.wallet}")
    private String configuredAdminWallet;

    @Transactional
    public String getNonce(String walletAddress) {
        String normalizedWallet = walletAddress.toLowerCase();
        User user = userRepository.findByWalletAddressIgnoreCase(normalizedWallet)
                .map(existingUser -> {
                    // Auto-promote to ADMIN if matches configured wallet
                    if (configuredAdminWallet != null && 
                        normalizedWallet.equalsIgnoreCase(configuredAdminWallet) && 
                        existingUser.getRole() != Role.ADMIN) {
                        
                        log.info("Promoting user {} to ADMIN based on configuration", normalizedWallet);
                        existingUser.setRole(Role.ADMIN);
                        existingUser.setPlan(Plan.PRO);
                        return userRepository.save(existingUser);
                    }
                    return existingUser;
                })
                .orElseGet(() -> {
                    // Create new user (Auto-Signup)
                    User newUser = new User(normalizedWallet);
                    
                    // Auto-promote to ADMIN if matches configured wallet
                    if (configuredAdminWallet != null && normalizedWallet.equalsIgnoreCase(configuredAdminWallet)) {
                        log.info("Creating new Admin user {} based on configuration", normalizedWallet);
                        newUser.setRole(Role.ADMIN);
                        newUser.setPlan(Plan.PRO);
                    }
                    
                    return userRepository.save(newUser);
                });
        
        // Always generate a new nonce for freshness
        String nonce = "Sign this message to login to CryptoArb: " + UUID.randomUUID().toString();
        user.setNonce(nonce);
        userRepository.save(user);
        
        return nonce;
    }

    @Transactional
    public AuthResponse verifyAndLogin(String walletAddress, String signature) {
        String normalizedWallet = walletAddress.toLowerCase();
        User user = userRepository.findByWalletAddressIgnoreCase(normalizedWallet)
                .orElseThrow(() -> new ResourceNotFoundException("User", "wallet", normalizedWallet));

        String nonceToCheck = user.getNonce();
        if (nonceToCheck == null || nonceToCheck.isEmpty()) {
            throw new BusinessException("NONCE_REQUIRED", "Please request a nonce first.");
        }

        boolean isValid = false;
        try {
            isValid = validateSignature(nonceToCheck, signature, normalizedWallet);
        } catch (Exception e) {
            log.warn("Signature validation failed for {}: {}", normalizedWallet, e.getMessage());
        }

        if (!isValid) {
            throw new BusinessException("INVALID_SIGNATURE", "Signature verification failed.");
        }

        // Auth successful: regenerate nonce (prevent replay)
        user.setNonce(null); 
        user.setLastLoginAt(java.time.LocalDateTime.now());
        userRepository.save(user);

        // Generate JWT
        String token = jwtService.generateToken(normalizedWallet, user.getRole());
        
        return AuthResponse.builder()
                .token(token)
                .role(user.getRole())
                .walletAddress(normalizedWallet)
                .build();
    }

    private boolean validateSignature(String message, String signature, String walletAddress) throws SignatureException {
        try {
            // Deconstruct signature
            byte[] signatureBytes = Numeric.hexStringToByteArray(signature);
            byte v = signatureBytes[64];
            if (v < 27) { 
                v += 27; 
            }
            
            Sign.SignatureData signatureData = new Sign.SignatureData(
                    v, 
                    Arrays.copyOfRange(signatureBytes, 0, 32), 
                    Arrays.copyOfRange(signatureBytes, 32, 64)
            );

            // Calculate hash of the prefixed message
            BigInteger publicKey = Sign.signedPrefixedMessageToKey(message.getBytes(StandardCharsets.UTF_8), signatureData);
            String recoveredAddress = "0x" + Keys.getAddress(publicKey);
            
            log.info("Signature Verification Debug:");
            log.info(" - Wallet Expected: {}", walletAddress);
            log.info(" - Wallet Recovered (Prefixed): {}", recoveredAddress);
            log.info(" - Nonce Expected: {}", message);
            
            if (recoveredAddress.equalsIgnoreCase(walletAddress)) {
                return true;
            }

            // Fallback 1: Try without prefix (Raw Message Hash)
            // Some wallets/libraries might sign the raw message hash directly
            BigInteger publicKeyRaw = Sign.signedMessageToKey(message.getBytes(StandardCharsets.UTF_8), signatureData);
            String recoveredAddressRaw = "0x" + Keys.getAddress(publicKeyRaw);
            log.info(" - Wallet Recovered (Raw): {}", recoveredAddressRaw);
            
            if (recoveredAddressRaw.equalsIgnoreCase(walletAddress)) {
                log.warn("Signature valid via RAW recovery (no prefix). This is non-standard but accepted.");
                return true;
            }
            
            return false;
        } catch (Exception e) {
            log.error("Error during signature recovery", e);
            throw e;
        }
    }
}
