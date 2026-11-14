package io.workflowengine.trigger.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.workflowengine.trigger.infrastructure.crypto.AesGcmSecretProtector;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class AesGcmSecretProtectorTest {

    private static final String KEY = Base64.getEncoder().encodeToString(new byte[32]);

    @Test
    void roundTripsAndUsesARandomNonce() {
        AesGcmSecretProtector p = new AesGcmSecretProtector(KEY);
        String a = p.encrypt("secret");
        assertThat(a).startsWith("v1:").isNotEqualTo(p.encrypt("secret"));
        assertThat(p.decrypt(a)).isEqualTo("secret");
    }

    @Test
    void tamperingOrAWrongKeyIsDetected() {
        AesGcmSecretProtector p = new AesGcmSecretProtector(KEY);
        String c = p.encrypt("secret");
        byte[] other = new byte[32];
        other[0] = 1;
        assertThatThrownBy(() -> new AesGcmSecretProtector(Base64.getEncoder().encodeToString(other)).decrypt(c))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void refusesMissingOrShortKeys() {
        assertThatThrownBy(() -> new AesGcmSecretProtector("")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new AesGcmSecretProtector(Base64.getEncoder().encodeToString(new byte[16])))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void tokensAreLongAndHashedDeterministically() {
        AesGcmSecretProtector p = new AesGcmSecretProtector(KEY);
        String token = p.newToken();
        assertThat(token).hasSize(43);
        assertThat(p.hashToken(token)).hasSize(64).isEqualTo(p.hashToken(token));
    }
}
