"use client";

import { ChevronDown } from "lucide-react";
import { useState, type ReactNode } from "react";
import { JsonViewer } from "@/components/json-viewer";
import { RelativeTime } from "@/components/relative-time";
import { NodeStatusBadge } from "@/components/status-badge";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Table, TableBody, TableCell, TableHead, TableHeader, TableRow } from "@/components/ui/table";
import type { NodeExecution } from "@/lib/api/types";
import { formatAbsolute, formatDuration, humanize } from "@/lib/format";
import { cn } from "@/lib/utils";

function Section({ title, children, defaultOpen = true }: { title: string; children: ReactNode; defaultOpen?: boolean }) {
  const [open, setOpen] = useState(defaultOpen);
  return (
    <Collapsible open={open} onOpenChange={setOpen}>
      <CollapsibleTrigger asChild>
        <button
          type="button"
          className="flex w-full items-center justify-between py-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
        >
          {title}
          <ChevronDown className={cn("size-4 transition-transform", open && "rotate-180")} aria-hidden />
        </button>
      </CollapsibleTrigger>
      <CollapsibleContent className="pt-2">{children}</CollapsibleContent>
    </Collapsible>
  );
}

function Meta({ label, children }: { label: string; children: ReactNode }) {
  return (
    <div>
      <dt className="text-xs text-muted-foreground">{label}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  );
}

export function NodeRunDetails({ node }: { node: NodeExecution }) {
  return (
    <div className="space-y-4" data-testid="node-run-details">
      <div className="flex flex-wrap items-center gap-2">
        <NodeStatusBadge status={node.status} />
        {node.branch ? (
          <span className="rounded-full border px-2 py-0.5 text-xs">
            branch: <strong>{node.branch}</strong>
          </span>
        ) : null}
        <span className="text-xs text-muted-foreground">{humanize(node.nodeType)}</span>
      </div>
      <dl className="grid grid-cols-2 gap-3">
        <Meta label="Duration">{formatDuration(node.durationMs)}</Meta>
        <Meta label="Attempts">
          <span data-testid="node-attempt-count">{node.attemptCount}</span>
        </Meta>
        <Meta label="Started">
          <RelativeTime value={node.startedAt} />
        </Meta>
        <Meta label="Finished">
          <RelativeTime value={node.finishedAt} />
        </Meta>
        {node.nextAttemptAt ? (
          <Meta label="Next attempt">
            <RelativeTime value={node.nextAttemptAt} />
          </Meta>
        ) : null}
        {node.resumeAt ? (
          <Meta label="Resumes">
            <RelativeTime value={node.resumeAt} />
          </Meta>
        ) : null}
      </dl>
      {node.error ? (
        <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm" data-testid="node-error">
          <p className="font-mono text-xs font-semibold text-destructive" data-testid="node-error-code">
            {node.error.code}
          </p>
          <p className="mt-1 break-words">{node.error.message}</p>
          <p className="mt-1 text-xs text-muted-foreground">{node.error.retryable ? "Retryable error" : "Non-retryable error"}</p>
        </div>
      ) : null}
      {node.attempts.length > 0 ? (
        <Section title={`Attempts (${node.attempts.length})`}>
          <div className="overflow-x-auto rounded-md border">
            <Table data-testid="attempts-table">
              <TableHeader>
                <TableRow>
                  <TableHead className="w-10">#</TableHead>
                  <TableHead>Status</TableHead>
                  <TableHead>Worker</TableHead>
                  <TableHead>Dispatched</TableHead>
                  <TableHead>Started</TableHead>
                  <TableHead>Finished</TableHead>
                  <TableHead>Error</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {node.attempts.map((attempt) => (
                  <TableRow key={attempt.attemptId} data-testid="attempt-row">
                    <TableCell>{attempt.number}</TableCell>
                    <TableCell className="text-xs">{humanize(attempt.status)}</TableCell>
                    <TableCell className="font-mono text-xs">{attempt.workerId ?? "—"}</TableCell>
                    <TableCell className="text-xs" title={formatAbsolute(attempt.dispatchedAt)}>
                      {formatAbsolute(attempt.dispatchedAt)}
                    </TableCell>
                    <TableCell className="text-xs">{formatAbsolute(attempt.startedAt)}</TableCell>
                    <TableCell className="text-xs">{formatAbsolute(attempt.finishedAt)}</TableCell>
                    <TableCell className="max-w-48 text-xs">
                      {attempt.errorCode ? (
                        <span title={attempt.errorMessage ?? undefined}>
                          <span className="font-mono text-destructive">{attempt.errorCode}</span>
                          {attempt.errorMessage ? <span className="block truncate text-muted-foreground">{attempt.errorMessage}</span> : null}
                        </span>
                      ) : (
                        "—"
                      )}
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </div>
        </Section>
      ) : null}
      <Section title="Input">
        <JsonViewer value={node.input} emptyLabel="No input recorded" />
      </Section>
      <Section title="Output">
        <JsonViewer value={node.output} emptyLabel="No output yet" />
      </Section>
    </div>
  );
}
