package io.workflowengine.trigger.domain.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * HMAC-SHA256 webhook signatures: {@code X-Webhook-Signature: sha256=hex(HMAC(secret, timestamp + "." + body))} with
 * {@code X-Webhook-Timestamp} in Unix seconds. A timestamp outside the tolerance window is rejected (replay
 * protection); identical signatures inside the window are deduplicated by the delivery key.
 */
public final class WebhookSignature {

    public static final Duration TOLERANCE = Duration.ofMinutes(5);

    private WebhookSignature() {}

    public static String sign(String secret, long timestamp, byte[] body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            mac.update(Long.toString(timestamp).getBytes(StandardCharsets.UTF_8));
            mac.update((byte) '.');
            mac.update(body);
            return "sha256=" + HexFormat.of().formatHex(mac.doFinal());
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public enum Verdict {
        VALID,
        MISSING,
        STALE_TIMESTAMP,
        MISMATCH
    }

    public static Verdict verify(String secret, String timestampHeader, String signatureHeader, byte[] body, Instant now) {
        if (timestampHeader == null || signatureHeader == null) {
            return Verdict.MISSING;
        }
        long timestamp;
        try {
            timestamp = Long.parseLong(timestampHeader.strip());
        } catch (NumberFormatException e) {
            return Verdict.MISSING;
        }
        if (Duration.between(Instant.ofEpochSecond(timestamp), now).abs().compareTo(TOLERANCE) > 0) {
            return Verdict.STALE_TIMESTAMP;
        }
        byte[] expected = sign(secret, timestamp, body).getBytes(StandardCharsets.UTF_8);
        byte[] actual = signatureHeader.strip().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual) ? Verdict.VALID : Verdict.MISMATCH;
    }
}
