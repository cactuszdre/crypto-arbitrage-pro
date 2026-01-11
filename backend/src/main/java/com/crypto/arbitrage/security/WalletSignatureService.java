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
        String normalizedAddress = walletAddress.toLowerCase();
        NonceEntry existingEntry = nonceStore.get(normalizedAddress);
        
        // If a valid (non-expired) nonce already exists, return it instead of generating a new one
        // This prevents race conditions when multiple nonce requests are made
        if (existingEntry != null) {
            long age = System.currentTimeMillis() - existingEntry.createdAt();
            // If nonce is less than 30 seconds old, reuse it
            if (age < 30_000) {
                log.info("[NONCE] Reusing existing nonce for wallet: {}. Nonce: {}..., Age: {}ms", 
                    normalizedAddress, 
                    existingEntry.nonce().substring(0, Math.min(10, existingEntry.nonce().length())),
                    age);
                return existingEntry.nonce();
            } else {
                log.info("[NONCE] Existing nonce too old ({}ms), generating new one for wallet: {}", 
                    age, normalizedAddress);
            }
        }
        
        String nonce = generateRandomNonce();
        log.info("[NONCE] Generating new nonce for wallet: {}. Nonce: {}...", 
            normalizedAddress, nonce.substring(0, Math.min(10, nonce.length())));
        nonceStore.put(normalizedAddress, new NonceEntry(nonce, System.currentTimeMillis()));
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
            String normalizedAddress = walletAddress.toLowerCase();
            log.info("[VERIFY] Starting verification for wallet: {}. Received nonce: {}...", 
                normalizedAddress, nonce.substring(0, Math.min(10, nonce.length())));
            
            // Check if nonce exists and hasn't expired
            NonceEntry entry = nonceStore.get(normalizedAddress);
            if (entry == null) {
                log.warn("[VERIFY] No nonce found for wallet: {}. Store size: {}", normalizedAddress, nonceStore.size());
                return false;
            }
            
            log.info("[VERIFY] Found stored nonce: {}... for wallet: {}", 
                entry.nonce().substring(0, Math.min(10, entry.nonce().length())), normalizedAddress);
            
            if (System.currentTimeMillis() - entry.createdAt() > NONCE_EXPIRY_MS) {
                log.warn("[VERIFY] Nonce expired for wallet: {}. Age: {}ms", normalizedAddress, 
                    System.currentTimeMillis() - entry.createdAt());
                nonceStore.remove(normalizedAddress);
                return false;
            }
            
            if (!entry.nonce().equals(nonce)) {
                log.warn("[VERIFY] Nonce mismatch for wallet: {}. Expected: {}..., Got: {}...", 
                    normalizedAddress,
                    entry.nonce().substring(0, Math.min(10, entry.nonce().length())),
                    nonce.substring(0, Math.min(10, nonce.length())));
                return false;
            }
            
            log.info("[VERIFY] Nonce matched for wallet: {}", normalizedAddress);

            // Create the message that was signed (EIP-191 personal_sign format)
            String message = createSignMessage(nonce);
            log.info("[VERIFY] Reconstructed message for signing (first 50 chars): {}", 
                message.substring(0, Math.min(50, message.length())));
            
            // Recover the address from the signature
            String recoveredAddress = recoverAddress(message, signature);
            log.info("[VERIFY] Recovered address from signature: {}", recoveredAddress);
            
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
            
            log.info("[VERIFY] Signature v value (raw): {}", v);

            // Create personal sign prefix (EIP-191)
            String prefix = "\u0019Ethereum Signed Message:\n" + message.length();
            String fullMessage = prefix + message;
            log.info("[VERIFY] Message length for prefix: {}", message.length());
            
            // Use UTF-8 encoding explicitly
            byte[] msgHash = Hash.sha3(fullMessage.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            log.info("[VERIFY] Message hash: {}", Numeric.toHexString(msgHash));

            // Normalize v if needed
            byte normalizedV = v;
            if (v < 27) {
                normalizedV = (byte)(v + 27);
            }
            
            // Try with normalized v first
            try {
                Sign.SignatureData sigData = new Sign.SignatureData(normalizedV, r, s);
                BigInteger pubKey = Sign.signedMessageHashToKey(msgHash, sigData);
                String recovered = "0x" + Keys.getAddress(pubKey);
                log.info("[VERIFY] Recovered with v={}: {}", normalizedV, recovered);
                return recovered;
            } catch (Exception e) {
                log.warn("[VERIFY] Recovery with v={} failed, trying alternative", normalizedV);
            }
            
            // Try with alternative v
            byte altV = (byte)(normalizedV == 27 ? 28 : 27);
            try {
                Sign.SignatureData sigData = new Sign.SignatureData(altV, r, s);
                BigInteger pubKey = Sign.signedMessageHashToKey(msgHash, sigData);
                String recovered = "0x" + Keys.getAddress(pubKey);
                log.info("[VERIFY] Recovered with alt v={}: {}", altV, recovered);
                return recovered;
            } catch (Exception e) {
                log.error("[VERIFY] All recovery attempts failed");
            }
            
            return null;

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
