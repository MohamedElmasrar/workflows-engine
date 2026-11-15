package io.workflowengine.trigger.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "scheduled_trigger")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ScheduledTriggerEntity {

    @Id
    @Setter(AccessLevel.NONE)
    private UUID id;

    @Setter(AccessLevel.NONE)
    @Column(name = "workflow_id", nullable = false, updatable = false)
    private UUID workflowId;

    @Column(name = "workflow_version", nullable = false)
    private int workflowVersion;

    @Column(nullable = false)
    private String cron;

    @Column(nullable = false)
    private String timezone;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private JsonNode payload;

    @Column(name = "next_fire_at")
    private Instant nextFireAt;

    @Column(name = "last_fired_at")
    private Instant lastFiredAt;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    ScheduledTriggerEntity(UUID id, UUID workflowId) {
        this.id = id;
        this.workflowId = workflowId;
    }
}
