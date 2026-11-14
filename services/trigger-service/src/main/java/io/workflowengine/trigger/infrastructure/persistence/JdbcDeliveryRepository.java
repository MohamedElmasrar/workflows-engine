package io.workflowengine.trigger.infrastructure.persistence;

import io.workflowengine.trigger.application.port.out.DeliveryRepository;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class JdbcDeliveryRepository implements DeliveryRepository {

    private final JdbcTemplate jdbc;

    public JdbcDeliveryRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public InsertResult insertIfAbsent(Delivery d) {
        int inserted = jdbc.update(
                """
                insert into trigger_delivery (id, workflow_id, trigger_type, dedupe_key, execution_id, execution_status,
                    payload_bytes, received_at)
                values (?, ?, ?, ?, ?, ?, ?, ?) on conflict (dedupe_key) do nothing
                """,
                d.id(), d.workflowId(), d.triggerType(), d.dedupeKey(), d.executionId(), d.executionStatus(),
                d.payloadBytes(), Timestamp.from(d.receivedAt()));
        if (inserted == 1) {
            return new InsertResult(d, true);
        }
        Delivery existing = jdbc.queryForObject(
                "select * from trigger_delivery where dedupe_key = ?", JdbcDeliveryRepository::map, d.dedupeKey());
        return new InsertResult(existing, false);
    }

    @Override
    public List<Delivery> recent(UUID workflowId, int limit) {
        return jdbc.query(
                "select * from trigger_delivery where workflow_id = ? order by received_at desc limit ?",
                JdbcDeliveryRepository::map,
                workflowId,
                limit);
    }

    @Override
    public void updateExecutionStatus(UUID executionId, String status) {
        jdbc.update("update trigger_delivery set execution_status = ? where execution_id = ?", status, executionId);
    }

    @Override
    public Optional<Delivery> byExecution(UUID executionId) {
        return jdbc.query("select * from trigger_delivery where execution_id = ?", JdbcDeliveryRepository::map, executionId)
                .stream()
                .findFirst();
    }

    private static Delivery map(ResultSet rs, int i) throws SQLException {
        return new Delivery(
                rs.getObject("id", UUID.class), rs.getObject("workflow_id", UUID.class), rs.getString("trigger_type"),
                rs.getString("dedupe_key"), rs.getObject("execution_id", UUID.class), rs.getString("execution_status"),
                rs.getInt("payload_bytes"), rs.getTimestamp("received_at").toInstant());
    }
}
