package io.workflowengine.execution.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Local projection of a workflow (owner, archive flag, ACL revision). Written by JDBC upserts, read with JPA. */
@Entity
@Table(name = "workflow_projection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkflowProjectionEntity {

    @Id
    @Column(name = "workflow_id")
    private UUID workflowId;

    @Column(name = "owner_id")
    private String ownerId;

    private String name;

    private boolean archived;

    @Column(name = "acl_revision")
    private long aclRevision;

    @Column(name = "latest_version")
    private Integer latestVersion;

    @Column(name = "updated_at")
    private Instant updatedAt;
}
