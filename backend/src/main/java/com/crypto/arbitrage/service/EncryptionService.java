package com.crypto.arbitrage.service;

import org.springframework.stereotype.Service;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import java.nio.charset.StandardCharsets;

@Service
public class EncryptionService {
    
    // In a real production app, this should be in an environment variable or vault
    @Value("${app.encryption.key}")
    private String secretKeyString; // Injected from properties

    private static final String ALGORITHM = "AES";

    private javax.crypto.SecretKey getSecretKey() {
        return new SecretKeySpec(secretKeyString.getBytes(StandardCharsets.UTF_8), ALGORITHM);
    }

    public String encrypt(String data) {
        if (data == null) return null;
        try {
            SecretKeySpec secretKey = (SecretKeySpec) getSecretKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey);
            return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes()));
        } catch (Exception e) {
            throw new RuntimeException("Error encrypting data", e);
        }
    }
    
    public String decrypt(String encryptedData) {
        if (encryptedData == null) return null;
        try {
            SecretKeySpec secretKey = (SecretKeySpec) getSecretKey();
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey);
            return new String(cipher.doFinal(Base64.getDecoder().decode(encryptedData)));
        } catch (Exception e) {
            throw new RuntimeException("Error decrypting data", e);
        }
    }
}
