package io.workflowengine.trigger.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Read side of {@code trigger_delivery}; rows are inserted with {@code ON CONFLICT DO NOTHING} (dedupe). */
@Entity
@Table(name = "trigger_delivery")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TriggerDeliveryEntity {

    @Id
    private UUID id;

    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "trigger_type")
    private String triggerType;

    @Column(name = "dedupe_key")
    private String dedupeKey;

    @Column(name = "execution_id")
    private UUID executionId;

    @Column(name = "execution_status")
    private String executionStatus;

    @Column(name = "payload_bytes")
    private int payloadBytes;

    @Column(name = "received_at")
    private Instant receivedAt;
}
