package io.workflowengine.trigger.infrastructure.scheduling;

import io.workflowengine.trigger.application.usecase.ScheduleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "trigger.scheduler.enabled", matchIfMissing = true)
public class SchedulePoller {

    private static final Logger log = LoggerFactory.getLogger(SchedulePoller.class);

    private final ScheduleService schedules;

    public SchedulePoller(ScheduleService schedules) {
        this.schedules = schedules;
    }

    @Scheduled(fixedDelayString = "${trigger.scheduler.interval:5s}")
    public void poll() {
        try {
            int fired;
            do {
                fired = schedules.fireDue(50);
                if (fired > 0) {
                    log.info("Fired {} scheduled trigger(s)", fired);
                }
            } while (fired == 50);
        } catch (RuntimeException e) {
            log.warn("Schedule poll failed: {}", e.getMessage());
        }
    }
}
