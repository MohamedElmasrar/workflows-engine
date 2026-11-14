package io.workflowengine.workflow.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.time.Instant;
import java.util.Objects;

@Embeddable
public class PermissionEmbeddable {

    @Column(name = "user_id", nullable = false)
    private String userId;

    @Column(nullable = false)
    private String role;

    @Column(name = "granted_by", nullable = false)
    private String grantedBy;

    @Column(name = "granted_at", nullable = false)
    private Instant grantedAt;

    protected PermissionEmbeddable() {}

    public PermissionEmbeddable(String userId, String role, String grantedBy, Instant grantedAt) {
        this.userId = userId;
        this.role = role;
        this.grantedBy = grantedBy;
        this.grantedAt = grantedAt;
    }

    public String getUserId() {
        return userId;
    }

    public String getRole() {
        return role;
    }

    public String getGrantedBy() {
        return grantedBy;
    }

    public Instant getGrantedAt() {
        return grantedAt;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof PermissionEmbeddable other && userId.equals(other.userId) && role.equals(other.role);
    }

    @Override
    public int hashCode() {
        return Objects.hash(userId, role);
    }
}
