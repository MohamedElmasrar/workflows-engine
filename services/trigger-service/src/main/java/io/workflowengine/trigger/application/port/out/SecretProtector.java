package io.workflowengine.trigger.application.port.out;

/** Encrypts secrets at rest with the application key and hashes webhook tokens for lookup. */
public interface SecretProtector {

    String encrypt(String plaintext);

    String decrypt(String ciphertext);

    String hashToken(String token);

    String newToken();
}
