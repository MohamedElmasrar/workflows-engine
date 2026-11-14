package io.workflowengine.trigger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.workflowengine.trigger.domain.service.WebhookSignature;
import io.workflowengine.trigger.domain.service.WebhookSignature.Verdict;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class WebhookSignatureTest {

    private static final byte[] BODY = "{\"a\":1}".getBytes(StandardCharsets.UTF_8);
    private static final Instant NOW = Instant.ofEpochSecond(1_700_000_000L);

    @Test
    void validSignatureWithinWindowIsAccepted() {
        String sig = WebhookSignature.sign("s3cret", NOW.getEpochSecond(), BODY);
        assertThat(WebhookSignature.verify("s3cret", "1700000000", sig, BODY, NOW.plusSeconds(60)))
                .isEqualTo(Verdict.VALID);
    }

    @Test
    void tamperedBodyWrongSecretOldTimestampAndMissingHeadersAreRejected() {
        String sig = WebhookSignature.sign("s3cret", NOW.getEpochSecond(), BODY);
        assertThat(WebhookSignature.verify("s3cret", "1700000000", sig, "{\"a\":2}".getBytes(), NOW))
                .isEqualTo(Verdict.MISMATCH);
        assertThat(WebhookSignature.verify("other", "1700000000", sig, BODY, NOW))
                .isEqualTo(Verdict.MISMATCH);
        assertThat(WebhookSignature.verify("s3cret", "1700000000", sig, BODY, NOW.plusSeconds(301)))
                .isEqualTo(Verdict.STALE_TIMESTAMP);
        assertThat(WebhookSignature.verify("s3cret", null, sig, BODY, NOW)).isEqualTo(Verdict.MISSING);
    }
}
