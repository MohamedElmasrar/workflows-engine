package io.workflowengine.workflow.infrastructure.persistence;

import io.workflowengine.workflow.application.port.out.UserDirectory;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class JdbcUserDirectory implements UserDirectory {

    private final JdbcTemplate jdbc;
    private final AppUserJpaRepository users;

    @Override
    public void remember(String userId, String username, String email) {
        jdbc.update(
                """
                insert into app_user (id, username, email, last_seen_at) values (?, ?, ?, now())
                on conflict (id) do update set username = excluded.username, email = excluded.email,
                    last_seen_at = excluded.last_seen_at
                """,
                userId,
                username,
                email);
    }

    @Override
    public Optional<String> findIdByUsername(String username) {
        return users.findByUsernameIgnoreCase(username).map(AppUserEntity::getId);
    }

    @Override
    public Map<String, String> usernames(Collection<String> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findByIdIn(userIds).stream()
                .collect(Collectors.toMap(AppUserEntity::getId, AppUserEntity::getUsername));
    }
}
