/** Newest-first list of execution history entries; node ids are clickable to select the node. */
import { NodeStatusBadge } from "@/components/status-badge";
import { type HistoryEntry, NODE_STATUSES, type NodeStatus } from "@/lib/api/types";
import { formatAbsolute, humanize } from "@/lib/format";
import { cn } from "@/lib/utils";
import { detailEntries } from "../lib/timeline-details";

export function ExecutionTimeline({ entries, onSelectNode, nodeNames }: { entries: HistoryEntry[]; onSelectNode: (id: string) => void; nodeNames: Map<string, string> }) {
  if (entries.length === 0) return <p className="text-sm text-muted-foreground">No events yet.</p>;
  return (
    <ol className="relative space-y-3 border-l pl-4" aria-label="Execution timeline" data-testid="timeline">
      {[...entries].reverse().map((entry) => {
        const status = entry.status && (NODE_STATUSES as readonly string[]).includes(entry.status) ? (entry.status as NodeStatus) : null;
        return (
          <li key={entry.sequence} className="relative" data-testid="timeline-entry">
            <span className="absolute top-1.5 -left-[21px] size-2.5 rounded-full border-2 border-background bg-muted-foreground" aria-hidden />
            <div className="flex flex-wrap items-center gap-2">
              <span className="font-mono text-[10px] text-muted-foreground">#{entry.sequence}</span>
              <span className="text-xs font-medium">{humanize(entry.type)}</span>
              {entry.nodeId ? (
                <button
                  type="button"
                  className="rounded bg-muted px-1.5 font-mono text-xs hover:bg-muted/70 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
                  onClick={() => onSelectNode(entry.nodeId as string)}
                  title={nodeNames.get(entry.nodeId) ?? entry.nodeId}
                >
                  {entry.nodeId}
                </button>
              ) : null}
              {status ? <NodeStatusBadge status={status} /> : entry.status ? <span className="text-xs">{humanize(entry.status)}</span> : null}
            </div>
            <p className="text-xs text-muted-foreground" title={formatAbsolute(entry.occurredAt)}>
              {formatAbsolute(entry.occurredAt)}
            </p>
            {Object.keys(entry.details).length > 0 ? (
              <dl className="mt-1 flex flex-wrap gap-x-3 gap-y-0.5 text-xs">
                {detailEntries(entry.details).map(([k, v]) => (
                  <div key={k} className="flex gap-1">
                    <dt className="text-muted-foreground">{k}:</dt>
                    <dd className={cn("font-mono break-all", k === "errorCode" && "text-destructive")}>{v}</dd>
                  </div>
                ))}
              </dl>
            ) : null}
          </li>
        );
      })}
    </ol>
  );
}
