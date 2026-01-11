package com.crypto.arbitrage.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.web3j.crypto.ECDSASignature;
import org.web3j.crypto.Hash;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for Ethereum wallet signature verification.
 * Implements the EIP-191 personal_sign standard used by MetaMask and WalletConnect.
 */
@Service
@Slf4j
public class WalletSignatureService {

    // Store nonces temporarily (in production, use Redis or database)
    private final Map<String, NonceEntry> nonceStore = new ConcurrentHashMap<>();
    private static final long NONCE_EXPIRY_MS = 5 * 60 * 1000; // 5 minutes

    private record NonceEntry(String nonce, long createdAt) {}

    /**
     * Generate a unique nonce for a wallet address.
     * The nonce must be signed to prove ownership.
     *
     * @param walletAddress The wallet address requesting authentication
     * @return The nonce to be signed
     */
    public String generateNonce(String walletAddress) {
        String nonce = generateRandomNonce();
        nonceStore.put(walletAddress.toLowerCase(), new NonceEntry(nonce, System.currentTimeMillis()));
        return nonce;
    }

    /**
     * Verify that a signature was created by the owner of the wallet.
     *
     * @param walletAddress The claimed wallet address
     * @param signature The signature (hex string starting with 0x)
     * @param nonce The nonce that was signed
     * @return true if the signature is valid and from the wallet owner
     */
    public boolean verifySignature(String walletAddress, String signature, String nonce) {
        try {
            // Check if nonce exists and hasn't expired
            NonceEntry entry = nonceStore.get(walletAddress.toLowerCase());
            if (entry == null) {
                log.warn("No nonce found for wallet: {}", walletAddress);
                return false;
            }
            
            if (System.currentTimeMillis() - entry.createdAt() > NONCE_EXPIRY_MS) {
                log.warn("Nonce expired for wallet: {}", walletAddress);
                nonceStore.remove(walletAddress.toLowerCase());
                return false;
            }
            
            if (!entry.nonce().equals(nonce)) {
                log.warn("Nonce mismatch for wallet: {}", walletAddress);
                return false;
            }

            // Create the message that was signed (EIP-191 personal_sign format)
            String message = createSignMessage(nonce);
            
            // Recover the address from the signature
            String recoveredAddress = recoverAddress(message, signature);
            
            if (recoveredAddress == null) {
                log.warn("Could not recover address from signature");
                return false;
            }

            // Compare addresses (case-insensitive)
            boolean valid = recoveredAddress.equalsIgnoreCase(walletAddress);
            
            if (valid) {
                // Remove used nonce
                nonceStore.remove(walletAddress.toLowerCase());
                log.info("Signature verified for wallet: {}", walletAddress);
            } else {
                log.warn("Signature verification failed. Expected: {}, Got: {}", walletAddress, recoveredAddress);
            }
            
            return valid;
            
        } catch (Exception e) {
            log.error("Error verifying signature: {}", e.getMessage(), e);
            return false;
        }
    }

    /**
     * Create the message format that wallets sign.
     * This creates a human-readable message for the user to verify.
     */
    public String createSignMessage(String nonce) {
        return "Sign this message to authenticate with Crypto Arbitrage Dashboard.\n\nNonce: " + nonce;
    }

    /**
     * Recover the Ethereum address from a signed message.
     * Uses EIP-191 personal_sign format.
     */
    private String recoverAddress(String message, String signatureHex) {
        try {
            // Remove 0x prefix if present
            String cleanSig = signatureHex.startsWith("0x") ? signatureHex.substring(2) : signatureHex;
            
            byte[] sigBytes = Numeric.hexStringToByteArray(cleanSig);
            if (sigBytes.length != 65) {
                log.error("Invalid signature length: {}", sigBytes.length);
                return null;
            }

            // Extract r, s, v from signature
            byte[] r = Arrays.copyOfRange(sigBytes, 0, 32);
            byte[] s = Arrays.copyOfRange(sigBytes, 32, 64);
            byte v = sigBytes[64];

            // Normalize v (some wallets use 0/1, others use 27/28)
            if (v < 27) {
                v += 27;
            }

            // Create personal sign prefix (EIP-191)
            String prefix = "\u0019Ethereum Signed Message:\n" + message.length();
            byte[] msgHash = Hash.sha3((prefix + message).getBytes());

            // Recover public key
            Sign.SignatureData sigData = new Sign.SignatureData(v, r, s);
            
            BigInteger pubKey = null;
            for (int i = 0; i < 4; i++) {
                byte recoveryId = (byte) (27 + (i % 2));
                try {
                    sigData = new Sign.SignatureData(recoveryId, r, s);
                    pubKey = Sign.signedMessageHashToKey(msgHash, sigData);
                    String recovered = "0x" + Keys.getAddress(pubKey);
                    if (recovered != null) {
                        return recovered;
                    }
                } catch (Exception ignored) {}
            }
            
            // Standard recovery
            sigData = new Sign.SignatureData(v, r, s);
            pubKey = Sign.signedMessageHashToKey(msgHash, sigData);
            return "0x" + Keys.getAddress(pubKey);

        } catch (Exception e) {
            log.error("Error recovering address: {}", e.getMessage(), e);
            return null;
        }
    }

    private String generateRandomNonce() {
        SecureRandom random = new SecureRandom();
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Numeric.toHexStringNoPrefix(bytes);
    }

    /**
     * Get the stored nonce for a wallet (for testing/debugging).
     */
    public String getStoredNonce(String walletAddress) {
        NonceEntry entry = nonceStore.get(walletAddress.toLowerCase());
        return entry != null ? entry.nonce() : null;
    }
}
