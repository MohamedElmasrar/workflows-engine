package io.workflowengine.trigger.domain;

import static org.assertj.core.api.Assertions.assertThat;

import io.workflowengine.trigger.domain.service.SchedulePolicy;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.support.CronExpression;

/** Pins the documented schedule semantics (time zones, DST, missed runs) to the actual cron library behaviour. */
class SchedulePolicyTest {

    private final SchedulePolicy policy =
            new SchedulePolicy((cron, after) -> CronExpression.parse("0 " + cron).next(after));

    @Test
    void evaluatesInTheConfiguredTimeZone() {
        // 09:00 in New York (EST, UTC-5) on a weekday
        Instant next = policy.nextFireAfter("0 9 * * MON-FRI", "America/New_York", Instant.parse("2025-01-06T12:00:00Z"));
        assertThat(next).isEqualTo(Instant.parse("2025-01-06T14:00:00Z"));
    }

    @Test
    void springForwardGapSkipsTheNonExistentLocalTime() {
        // Europe/Berlin jumps from 02:00 to 03:00 on 2025-03-30; 02:30 does not exist that day and is skipped
        Instant next = policy.nextFireAfter("30 2 * * *", "Europe/Berlin", Instant.parse("2025-03-29T12:00:00Z"));
        assertThat(next).isEqualTo(Instant.parse("2025-03-31T00:30:00Z"));
    }

    @Test
    void fallBackOverlapFiresOnlyOnce() {
        // 02:30 occurs twice in Europe/Berlin on 2025-10-26 (CEST then CET); only the first occurrence fires
        Instant first = policy.nextFireAfter("30 2 * * *", "Europe/Berlin", Instant.parse("2025-10-25T12:00:00Z"));
        assertThat(first).isEqualTo(Instant.parse("2025-10-26T00:30:00Z"));
        Instant second = policy.nextFireAfter("30 2 * * *", "Europe/Berlin", first);
        assertThat(second).isEqualTo(Instant.parse("2025-10-27T01:30:00Z"));
    }

    @Test
    void missedRunsAreCoalescedIntoOneAndTheScheduleContinuesFromNow() {
        Instant missed = Instant.parse("2025-01-01T00:00:00Z");
        Instant now = Instant.parse("2025-01-01T05:30:00Z"); // five hourly runs missed
        Instant next = policy.rescheduleAfterFiring("0 * * * *", "UTC", missed, now);
        assertThat(next).isEqualTo(Instant.parse("2025-01-01T06:00:00Z"));
    }
}
