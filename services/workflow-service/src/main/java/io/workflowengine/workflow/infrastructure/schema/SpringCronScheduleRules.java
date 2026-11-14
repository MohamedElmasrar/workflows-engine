package io.workflowengine.workflow.infrastructure.schema;

import io.workflowengine.workflow.domain.service.ScheduleRules;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

/**
 * Accepts standard 5-field cron expressions (minute resolution). The trigger service evaluates them with the same
 * library by prefixing a seconds field of {@code 0}.
 */
@Component
public class SpringCronScheduleRules implements ScheduleRules {

    @Override
    public String cronProblem(String cron) {
        String[] fields = cron.strip().split("\\s+");
        if (fields.length != 5) {
            return "Cron expression must have 5 fields (minute hour day-of-month month day-of-week)";
        }
        try {
            CronExpression.parse("0 " + cron.strip());
            return null;
        } catch (IllegalArgumentException e) {
            return "Invalid cron expression: " + e.getMessage();
        }
    }
}
