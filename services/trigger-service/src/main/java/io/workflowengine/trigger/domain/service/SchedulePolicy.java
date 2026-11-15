package io.workflowengine.trigger.domain.service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.function.BiFunction;
import lombok.RequiredArgsConstructor;

/**
 * Schedule semantics:
 * <ul>
 *   <li>Cron expressions have 5 fields and are evaluated in the trigger's IANA time zone.</li>
 *   <li>Missed fire times (scheduler down) are coalesced: one execution is started for the most recent missed time,
 *       then the schedule continues from the next time after "now".</li>
 *   <li>DST spring-forward: a local time that does not exist that day is skipped for that day (the next fire is the
 *       following matching local time).</li>
 *   <li>DST fall-back: a local time that occurs twice fires once, at its first occurrence.</li>
 * </ul>
 */
@RequiredArgsConstructor
public final class SchedulePolicy {

    private final BiFunction<String, ZonedDateTime, ZonedDateTime> nextAfter;

    public Instant nextFireAfter(String cron, String timezone, Instant after) {
        ZonedDateTime base = after.atZone(ZoneId.of(timezone));
        ZonedDateTime next = nextAfter.apply(cron, base);
        // In a fall-back overlap the cron library matches the same wall-clock time again with the later offset.
        while (next != null
                && next.toLocalDateTime().equals(base.toLocalDateTime())
                && !next.getOffset().equals(base.getOffset())) {
            next = nextAfter.apply(cron, next);
        }
        if (next == null) {
            throw new IllegalArgumentException("Cron expression '" + cron + "' never fires");
        }
        return next.toInstant();
    }

    /** Next fire time strictly after {@code now}, used after firing (coalescing all missed occurrences). */
    public Instant rescheduleAfterFiring(String cron, String timezone, Instant firedFor, Instant now) {
        Instant base = now.isAfter(firedFor) ? now : firedFor;
        return nextFireAfter(cron, timezone, base);
    }
}
