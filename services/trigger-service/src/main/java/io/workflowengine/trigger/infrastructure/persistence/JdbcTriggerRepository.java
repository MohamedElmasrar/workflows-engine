package io.workflowengine.trigger.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.workflowengine.trigger.application.port.out.TriggerRepository;
import io.workflowengine.trigger.domain.model.ScheduledTrigger;
import io.workflowengine.trigger.domain.model.WebhookEndpoint;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Plain JDBC: the tables are flat and the scheduler relies on SKIP LOCKED, which is clearest in SQL. */
@Repository
public class JdbcTriggerRepository implements TriggerRepository {

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public JdbcTriggerRepository(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public boolean registerVersion(UUID workflowId, int version, String kind) {
        return jdbc.update(
                        """
                        insert into trigger_registration (workflow_id, workflow_version, trigger_kind) values (?, ?, ?)
                        on conflict (workflow_id) do update set workflow_version = excluded.workflow_version,
                            trigger_kind = excluded.trigger_kind, updated_at = now()
                        where trigger_registration.workflow_version < excluded.workflow_version
                        """,
                        workflowId,
                        version,
                        kind)
                == 1;
    }

    @Override
    public Optional<WebhookEndpoint> webhookByTokenHash(String tokenHash) {
        return jdbc.query("select * from webhook_registration where token_hash = ?", this::webhook, tokenHash).stream()
                .findFirst();
    }

    @Override
    public Optional<WebhookEndpoint> webhookByWorkflow(UUID workflowId) {
        return jdbc
                .query("select * from webhook_registration where workflow_id = ?", this::webhook, workflowId)
                .stream()
                .findFirst();
    }

    @Override
    public void saveWebhook(WebhookEndpoint w) {
        jdbc.update(
                """
                insert into webhook_registration (id, workflow_id, workflow_version, token_hash, token_encrypted,
                    secret_encrypted, require_signature, required_fields, active)
                values (?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?)
                on conflict (id) do update set workflow_version = excluded.workflow_version,
                    secret_encrypted = excluded.secret_encrypted, require_signature = excluded.require_signature,
                    required_fields = excluded.required_fields, active = excluded.active, updated_at = now()
                """,
                w.id(),
                w.workflowId(),
                w.workflowVersion(),
                w.tokenHash(),
                w.encryptedToken(),
                w.encryptedSecret(),
                w.requireSignature(),
                write(w.requiredFields()),
                w.active());
    }

    @Override
    public Optional<ScheduledTrigger> scheduleByWorkflow(UUID workflowId) {
        return jdbc.query("select * from scheduled_trigger where workflow_id = ?", this::schedule, workflowId).stream()
                .findFirst();
    }

    @Override
    public void saveSchedule(ScheduledTrigger s) {
        jdbc.update(
                """
                insert into scheduled_trigger (id, workflow_id, workflow_version, cron, timezone, payload, next_fire_at,
                    last_fired_at, active)
                values (?, ?, ?, ?, ?, ?::jsonb, ?, ?, ?)
                on conflict (id) do update set workflow_version = excluded.workflow_version, cron = excluded.cron,
                    timezone = excluded.timezone, payload = excluded.payload, next_fire_at = excluded.next_fire_at,
                    last_fired_at = excluded.last_fired_at, active = excluded.active, updated_at = now()
                """,
                s.id(),
                s.workflowId(),
                s.workflowVersion(),
                s.cron(),
                s.timezone(),
                write(s.payload()),
                ts(s.nextFireAt()),
                ts(s.lastFiredAt()),
                s.active());
    }

    @Override
    public List<ScheduledTrigger> lockDueSchedules(Instant now, int limit) {
        return jdbc.query(
                """
                select * from scheduled_trigger where active and next_fire_at <= ?
                order by next_fire_at limit ? for update skip locked
                """,
                this::schedule,
                ts(now),
                limit);
    }

    @Override
    public void deactivateAll(UUID workflowId) {
        jdbc.update(
                "update webhook_registration set active = false, updated_at = now() where workflow_id = ?", workflowId);
        jdbc.update(
                "update scheduled_trigger set active = false, updated_at = now() where workflow_id = ?", workflowId);
    }

    private WebhookEndpoint webhook(ResultSet rs, int i) throws SQLException {
        List<String> fields = new ArrayList<>();
        read(rs.getString("required_fields")).forEach(f -> fields.add(f.asText()));
        return new WebhookEndpoint(
                rs.getObject("id", UUID.class),
                rs.getObject("workflow_id", UUID.class),
                rs.getInt("workflow_version"),
                rs.getString("token_hash"),
                rs.getString("token_encrypted"),
                rs.getString("secret_encrypted"),
                rs.getBoolean("require_signature"),
                fields,
                rs.getBoolean("active"));
    }

    private ScheduledTrigger schedule(ResultSet rs, int i) throws SQLException {
        return new ScheduledTrigger(
                rs.getObject("id", UUID.class),
                rs.getObject("workflow_id", UUID.class),
                rs.getInt("workflow_version"),
                rs.getString("cron"),
                rs.getString("timezone"),
                read(rs.getString("payload")),
                instant(rs.getTimestamp("next_fire_at")),
                instant(rs.getTimestamp("last_fired_at")),
                rs.getBoolean("active"));
    }

    private String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private JsonNode read(String value) {
        try {
            return json.readTree(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static Timestamp ts(Instant i) {
        return i == null ? null : Timestamp.from(i);
    }

    static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
