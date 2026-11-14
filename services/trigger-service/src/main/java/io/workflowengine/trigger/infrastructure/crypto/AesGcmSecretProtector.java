package io.workflowengine.trigger.infrastructure.crypto;

import io.workflowengine.trigger.application.port.out.SecretProtector;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM with a random 96-bit nonce per value; ciphertext format {@code v1:base64(nonce || ciphertext+tag)}. The
 * key comes from configuration (environment / secret store), never from the database. The version prefix allows key
 * rotation later.
 */
public class AesGcmSecretProtector implements SecretProtector {

    private static final int NONCE_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmSecretProtector(String base64Key) {
        if (base64Key == null || base64Key.isBlank()) {
            throw new IllegalStateException("trigger.encryption-key is not configured (base64 encoded 32 bytes)");
        }
        byte[] raw = Base64.getDecoder().decode(base64Key.strip());
        if (raw.length != 32) {
            throw new IllegalStateException("trigger.encryption-key must decode to 32 bytes");
        }
        this.key = new SecretKeySpec(raw, "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        try {
            byte[] nonce = new byte[NONCE_BYTES];
            random.nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, nonce));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return "v1:"
                    + Base64.getEncoder()
                            .encodeToString(ByteBuffer.allocate(nonce.length + encrypted.length)
                                    .put(nonce)
                                    .put(encrypted)
                                    .array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Encryption failed", e);
        }
    }

    @Override
    public String decrypt(String ciphertext) {
        if (!ciphertext.startsWith("v1:")) {
            throw new IllegalStateException("Unknown ciphertext version");
        }
        try {
            byte[] data = Base64.getDecoder().decode(ciphertext.substring(3));
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, data, 0, NONCE_BYTES));
            return new String(cipher.doFinal(data, NONCE_BYTES, data.length - NONCE_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Decryption failed (wrong key or tampered value)", e);
        }
    }

    @Override
    public String hashToken(String token) {
        try {
            return HexFormat.of()
                    .formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public String newToken() {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
