package io.workflowengine.workflow.domain.service;

/** Domain-owned check for cron expressions, implemented with the scheduler library used by the trigger service. */
@FunctionalInterface
public interface ScheduleRules {

    /** Returns null when the expression is valid, otherwise a human-readable reason. */
    String cronProblem(String cron);
}
