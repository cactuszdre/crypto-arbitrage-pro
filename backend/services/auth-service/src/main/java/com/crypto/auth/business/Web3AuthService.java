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
            byte originalV = signatureBytes[64];
            byte[] r = Arrays.copyOfRange(signatureBytes, 0, 32);
            byte[] s = Arrays.copyOfRange(signatureBytes, 32, 64);
            
            // Try different v values (27, 28, or raw 0, 1)
            byte[] vValuesToTry = originalV < 27 
                ? new byte[]{(byte)(originalV + 27), (byte)(27), (byte)(28)} 
                : new byte[]{originalV, (byte)(27), (byte)(28)};
            
            log.info("Signature Verification Debug:");
            log.info(" - Wallet Expected: {}", walletAddress);
            log.info(" - Nonce/Message: {}", message);
            log.info(" - Original v value: {}", originalV);
            
            for (byte v : vValuesToTry) {
                Sign.SignatureData signatureData = new Sign.SignatureData(v, r, s);
                
                // Try with Ethereum prefix (EIP-191 personal_sign)
                try {
                    BigInteger publicKey = Sign.signedPrefixedMessageToKey(message.getBytes(StandardCharsets.UTF_8), signatureData);
                    String recoveredAddress = "0x" + Keys.getAddress(publicKey);
                    log.debug(" - Wallet Recovered (v={}, Prefixed): {}", v, recoveredAddress);
                    
                    if (recoveredAddress.equalsIgnoreCase(walletAddress)) {
                        log.info(" - ✅ Match found with v={} (prefixed)", v);
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("Recovery failed with v={} (prefixed): {}", v, e.getMessage());
                }
                
                // Try without prefix (Raw Message Hash) - some wallets do this
                try {
                    BigInteger publicKeyRaw = Sign.signedMessageToKey(message.getBytes(StandardCharsets.UTF_8), signatureData);
                    String recoveredAddressRaw = "0x" + Keys.getAddress(publicKeyRaw);
                    log.debug(" - Wallet Recovered (v={}, Raw): {}", v, recoveredAddressRaw);
                    
                    if (recoveredAddressRaw.equalsIgnoreCase(walletAddress)) {
                        log.warn(" - ✅ Match found with v={} (raw, non-standard)", v);
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("Raw recovery failed with v={}: {}", v, e.getMessage());
                }
            }
            
            log.warn(" - ❌ No matching address found after trying all v values");
            return false;
        } catch (Exception e) {
            log.error("Error during signature recovery", e);
            throw e;
        }
    }
}
