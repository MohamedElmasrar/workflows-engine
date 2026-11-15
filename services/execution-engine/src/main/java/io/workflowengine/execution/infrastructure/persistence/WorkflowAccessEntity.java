package io.workflowengine.execution.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** One ACL entry of the workflow projection. */
@Entity
@Table(name = "workflow_access")
@IdClass(WorkflowAccessEntity.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowAccessEntity {

    public record Key(UUID workflowId, String userId) implements Serializable {
        public Key() {
            this(null, null);
        }
    }

    @Id
    @Column(name = "workflow_id")
    private UUID workflowId;

    @Id
    @Column(name = "user_id")
    private String userId;

    private String role;
}
