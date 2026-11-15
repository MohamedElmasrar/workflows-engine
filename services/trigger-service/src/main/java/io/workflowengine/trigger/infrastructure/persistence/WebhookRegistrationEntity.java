package io.workflowengine.trigger.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "webhook_registration")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WebhookRegistrationEntity {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Setter(AccessLevel.NONE)
    @Column(name = "workflow_id", nullable = false, updatable = false)
    private UUID workflowId;

    @Column(name = "workflow_version", nullable = false)
    private int workflowVersion;

    @Setter(AccessLevel.NONE)
    @Column(name = "token_hash", nullable = false, updatable = false)
    private String tokenHash;

    @Setter(AccessLevel.NONE)
    @Column(name = "token_encrypted", nullable = false, updatable = false)
    private String tokenEncrypted;

    @Column(name = "secret_encrypted", nullable = false)
    private String secretEncrypted;

    @Column(name = "require_signature", nullable = false)
    private boolean requireSignature;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "required_fields", nullable = false)
    private List<String> requiredFields;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    WebhookRegistrationEntity(UUID id, UUID workflowId, String tokenHash, String tokenEncrypted) {
        this.id = id;
        this.workflowId = workflowId;
        this.tokenHash = tokenHash;
        this.tokenEncrypted = tokenEncrypted;
    }
}
