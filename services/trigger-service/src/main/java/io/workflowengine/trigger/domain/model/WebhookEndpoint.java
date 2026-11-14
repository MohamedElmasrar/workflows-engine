package io.workflowengine.trigger.domain.model;

import java.util.List;
import java.util.UUID;

/**
 * Webhook registration. {@code tokenHash} is used for lookup; the token and signing secret are stored only encrypted.
 */
public record WebhookEndpoint(
        UUID id,
        UUID workflowId,
        int workflowVersion,
        String tokenHash,
        String encryptedToken,
        String encryptedSecret,
        boolean requireSignature,
        List<String> requiredFields,
        boolean active) {}
