package io.workflowengine.workflow.infrastructure.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUserEntity {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    private String email;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;
}
