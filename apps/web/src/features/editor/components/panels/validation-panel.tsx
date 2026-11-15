"use client";
/** List of validation issues from the server; clicking an issue selects its node or edge. */
import { AlertTriangle, CheckCircle2, X } from "lucide-react";
import { useReactFlow } from "@xyflow/react";
import { Button } from "@/components/ui/button";
import type { ValidationIssue } from "@/lib/api/types";
import { useEditorStore } from "@/stores/editor/editor-store";

export function ValidationPanel({ issues, valid, onClose }: { issues: ValidationIssue[]; valid: boolean; onClose: () => void }) {
  const selectNode = useEditorStore((s) => s.selectNode);
  const selectEdge = useEditorStore((s) => s.selectEdge);
  const { fitView } = useReactFlow();
  return (
    <section
      aria-label="Validation results"
      className="absolute bottom-3 left-3 z-10 max-h-64 w-[min(28rem,calc(100%-1.5rem))] overflow-auto rounded-lg border bg-background shadow-lg"
      data-testid="validation-panel"
    >
      <header className="sticky top-0 flex items-center justify-between border-b bg-background px-3 py-2">
        <p className="flex items-center gap-2 text-sm font-medium">
          {valid ? (
            <>
              <CheckCircle2 className="size-4 text-emerald-600" aria-hidden /> Workflow is valid
            </>
          ) : (
            <>
              <AlertTriangle className="size-4 text-destructive" aria-hidden /> {issues.length} problem{issues.length === 1 ? "" : "s"} found
            </>
          )}
        </p>
        <Button variant="ghost" size="icon-xs" onClick={onClose} aria-label="Close validation results">
          <X />
        </Button>
      </header>
      {issues.length > 0 ? (
        <ul className="divide-y">
          {issues.map((issue, index) => (
            <li key={index}>
              <button
                type="button"
                className="w-full px-3 py-2 text-left text-sm hover:bg-muted/50 focus-visible:bg-muted focus-visible:outline-none"
                onClick={() => {
                  if (issue.nodeId) {
                    selectNode(issue.nodeId);
                    void fitView({ nodes: [{ id: issue.nodeId }], duration: 300, maxZoom: 1.2 });
                  } else if (issue.edgeId) {
                    selectEdge(issue.edgeId);
                  }
                }}
              >
                <span className="font-mono text-xs font-semibold text-destructive">{issue.code}</span>
                {issue.nodeId ? <span className="ml-2 font-mono text-xs text-muted-foreground">node {issue.nodeId}</span> : null}
                {issue.edgeId ? <span className="ml-2 font-mono text-xs text-muted-foreground">edge {issue.edgeId}</span> : null}
                <span className="block text-muted-foreground">{issue.message}</span>
              </button>
            </li>
          ))}
        </ul>
      ) : null}
    </section>
  );
}
