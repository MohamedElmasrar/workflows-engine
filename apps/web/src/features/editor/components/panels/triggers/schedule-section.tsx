"use client";
/** Schedule registration: cron, time zone, next and last fire time. */
import { CalendarClock } from "lucide-react";
import { RelativeTime } from "@/components/relative-time";
import type { ScheduleTrigger } from "@/lib/api/types";
import { formatAbsolute } from "@/lib/format";

export function ScheduleSection({ schedule }: { schedule: ScheduleTrigger }) {
  return (
    <section className="space-y-3 rounded-lg border p-4" aria-labelledby="schedule-heading">
      <h3 id="schedule-heading" className="flex items-center gap-2 text-sm font-semibold">
        <CalendarClock className="size-4" aria-hidden /> Schedule {schedule.active ? "" : "(inactive)"}
      </h3>
      <dl className="grid grid-cols-2 gap-2 text-sm">
        <dt className="text-muted-foreground">Cron</dt>
        <dd className="font-mono">{schedule.cron}</dd>
        <dt className="text-muted-foreground">Timezone</dt>
        <dd>{schedule.timezone}</dd>
        <dt className="text-muted-foreground">Next fire</dt>
        <dd title={formatAbsolute(schedule.nextFireAt)}>
          {schedule.nextFireAt ? formatAbsolute(schedule.nextFireAt) : "—"}
        </dd>
        <dt className="text-muted-foreground">Last fired</dt>
        <dd>
          <RelativeTime value={schedule.lastFiredAt} />
        </dd>
      </dl>
    </section>
  );
}
