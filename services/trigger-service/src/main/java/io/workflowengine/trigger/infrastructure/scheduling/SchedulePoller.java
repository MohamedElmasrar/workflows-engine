package io.workflowengine.trigger.infrastructure.scheduling;

import io.workflowengine.trigger.application.usecase.ScheduleService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(value = "trigger.scheduler.enabled", matchIfMissing = true)
@Slf4j
@RequiredArgsConstructor
public class SchedulePoller {

    private final ScheduleService schedules;

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
