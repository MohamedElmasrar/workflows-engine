"use client";
/** Top bar of the editor: name and save state, last-run toggle, layout, validate, save, publish, run and the sheet menu. */
import { ArrowLeft, CheckCircle2, CircleDot, History, LayoutGrid, Loader2, MoreHorizontal, Play, Rocket, Save, Share2, ShieldCheck, Zap } from "lucide-react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { ExecutionStatusBadge, WorkflowStatusBadge } from "@/components/status-badge";
import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import { Switch } from "@/components/ui/switch";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import type { Workflow } from "@/lib/api/types";
import { humanize } from "@/lib/format";
import { usePreferences } from "@/lib/preferences";
import { useEditorStore } from "@/stores/editor/editor-store";
import type { useEditorActions } from "../hooks/use-editor-actions";
import type { useLastRun } from "../hooks/use-last-run";

export type EditorSheet = "versions" | "triggers" | "sharing";

export function EditorToolbar({
  workflow,
  actions,
  lastRun,
  onRun,
  onOpenSheet,
}: {
  workflow: Workflow;
  actions: ReturnType<typeof useEditorActions>;
  lastRun: ReturnType<typeof useLastRun>;
  onRun: () => void;
  onOpenSheet: (sheet: EditorSheet) => void;
}) {
  const router = useRouter();
  const [prefs] = usePreferences();
  const dirty = useEditorStore((s) => s.dirty);
  const readOnly = useEditorStore((s) => s.readOnly);
  const role = useEditorStore((s) => s.role);
  const name = useEditorStore((s) => s.meta.name);
  const applyLayout = useEditorStore((s) => s.applyLayout);
  const canRun = workflow.latestVersion !== null && role !== "VIEWER" && workflow.status !== "ARCHIVED";
  const busy = actions.saving || actions.publishing;

  return (
    <div className="flex flex-wrap items-center gap-2 border-b px-3 py-2" role="toolbar" aria-label="Editor toolbar">
      <Button variant="ghost" size="icon-sm" onClick={() => router.push("/workflows")} aria-label="Back to workflows">
        <ArrowLeft />
      </Button>
      <div className="mr-auto flex min-w-0 items-center gap-2">
        <h1 className="truncate text-sm font-semibold" title={name}>
          {name}
        </h1>
        <WorkflowStatusBadge status={workflow.status} />
        {workflow.latestVersion ? <span className="text-xs text-muted-foreground">v{workflow.latestVersion}</span> : null}
        {readOnly ? (
          <span className="rounded border px-1.5 py-0.5 text-xs text-muted-foreground">Read-only · {humanize(role)}</span>
        ) : dirty ? (
          <span className="flex items-center gap-1 text-xs text-amber-700" data-testid="dirty-indicator">
            <CircleDot className="size-3" aria-hidden /> Unsaved changes
          </span>
        ) : (
          <span className="flex items-center gap-1 text-xs text-muted-foreground" data-testid="saved-indicator">
            <CheckCircle2 className="size-3" aria-hidden /> Saved
            {workflow.hasUnpublishedChanges && workflow.latestVersion ? " · unpublished changes" : ""}
          </span>
        )}
      </div>
      <label className="flex items-center gap-2 text-xs">
        <Switch checked={lastRun.enabled} onCheckedChange={lastRun.setEnabled} aria-label="Show last run" />
        <span className="hidden lg:inline">Show last run</span>
      </label>
      {lastRun.execution ? (
        <Link href={`/executions/${lastRun.execution.id}`} className="hidden items-center gap-1 text-xs underline-offset-2 hover:underline md:flex">
          <ExecutionStatusBadge status={lastRun.execution.status} />
        </Link>
      ) : null}
      {!readOnly ? (
        <>
          <Tooltip>
            <TooltipTrigger asChild>
              <Button variant="outline" size="sm" onClick={() => applyLayout(prefs.layoutDirection)} aria-label="Auto layout">
                <LayoutGrid /> <span className="hidden xl:inline">Layout</span>
              </Button>
            </TooltipTrigger>
            <TooltipContent>Arrange nodes automatically ({prefs.layoutDirection === "LR" ? "left to right" : "top to bottom"})</TooltipContent>
          </Tooltip>
          <Button variant="outline" size="sm" onClick={() => void actions.validate()} disabled={actions.validating} data-testid="validate-button">
            {actions.validating ? <Loader2 className="animate-spin" /> : <ShieldCheck />} Validate
          </Button>
          <Button variant="outline" size="sm" onClick={() => void actions.save()} disabled={busy || !dirty} data-testid="save-button">
            {actions.saving ? <Loader2 className="animate-spin" /> : <Save />} Save
          </Button>
          <Button variant="outline" size="sm" onClick={() => void actions.publish()} disabled={busy} data-testid="publish-button">
            {actions.publishing ? <Loader2 className="animate-spin" /> : <Rocket />} Publish
          </Button>
        </>
      ) : null}
      <Tooltip>
        <TooltipTrigger asChild>
          <span>
            <Button size="sm" onClick={onRun} disabled={!canRun} data-testid="run-button">
              <Play /> Run
            </Button>
          </span>
        </TooltipTrigger>
        {!canRun ? <TooltipContent>{role === "VIEWER" ? "Viewers cannot run workflows" : "Publish a version first"}</TooltipContent> : null}
      </Tooltip>
      <DropdownMenu>
        <DropdownMenuTrigger asChild>
          <Button variant="ghost" size="icon-sm" aria-label="More actions">
            <MoreHorizontal />
          </Button>
        </DropdownMenuTrigger>
        <DropdownMenuContent align="end">
          <DropdownMenuItem onSelect={() => onOpenSheet("versions")}>
            <History /> Versions
          </DropdownMenuItem>
          <DropdownMenuItem onSelect={() => onOpenSheet("triggers")}>
            <Zap /> Triggers
          </DropdownMenuItem>
          {role === "OWNER" ? (
            <>
              <DropdownMenuSeparator />
              <DropdownMenuItem onSelect={() => onOpenSheet("sharing")}>
                <Share2 /> Sharing
              </DropdownMenuItem>
            </>
          ) : null}
        </DropdownMenuContent>
      </DropdownMenu>
    </div>
  );
}
