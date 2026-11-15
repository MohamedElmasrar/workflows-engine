package io.workflowengine.trigger.application.usecase;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.workflowengine.contracts.execution.WorkflowExecutionRequested;
import io.workflowengine.trigger.application.port.out.DeliveryRepository;
import io.workflowengine.trigger.application.port.out.ExecutionRequestPublisher;
import io.workflowengine.trigger.application.port.out.TriggerRepository;
import io.workflowengine.trigger.domain.model.ScheduledTrigger;
import io.workflowengine.trigger.domain.service.SchedulePolicy;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

/**
 * Fires due schedules. Rows are locked with SKIP LOCKED and the delivery dedupe key is
 * {@code schedule:<id>:<fireTime>}, so even two replicas racing on the same row cannot start two executions for one
 * fire time.
 */
@RequiredArgsConstructor
public class ScheduleService {

    private final TriggerRepository triggers;
    private final DeliveryRepository deliveries;
    private final ExecutionRequestPublisher publisher;
    private final SchedulePolicy policy;
    private final ObjectMapper objectMapper;
    private final Clock clock;

    @Transactional
    public int fireDue(int limit) {
        Instant now = clock.instant();
        List<ScheduledTrigger> due = triggers.lockDueSchedules(now, limit);
        for (ScheduledTrigger schedule : due) {
            Instant fireTime = schedule.nextFireAt();
            String dedupeKey = "schedule:" + schedule.id() + ":" + fireTime;
            UUID executionId = UUID.randomUUID();
            DeliveryRepository.InsertResult result = deliveries.insertIfAbsent(new DeliveryRepository.Delivery(
                    UUID.randomUUID(), schedule.workflowId(), "SCHEDULE", dedupeKey, executionId, "REQUESTED", 0, now));
            if (result.created()) {
                ObjectNode input = objectMapper
                        .createObjectNode()
                        .put("scheduledAt", fireTime.toString())
                        .put("firedAt", now.toString());
                input.set("payload", schedule.payload());
                publisher.publish(new WorkflowExecutionRequested(
                        executionId,
                        schedule.workflowId(),
                        schedule.workflowVersion(),
                        "SCHEDULE",
                        "schedule:" + schedule.id(),
                        input,
                        dedupeKey));
            }
            Instant next = policy.rescheduleAfterFiring(schedule.cron(), schedule.timezone(), fireTime, now);
            triggers.saveSchedule(new ScheduledTrigger(
                    schedule.id(),
                    schedule.workflowId(),
                    schedule.workflowVersion(),
                    schedule.cron(),
                    schedule.timezone(),
                    schedule.payload(),
                    next,
                    fireTime,
                    true));
        }
        return due.size();
    }
}
