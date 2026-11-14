"use client";

import { ReactFlowProvider } from "@xyflow/react";
import {
  ArrowLeft,
  CheckCircle2,
  CircleDot,
  History,
  LayoutGrid,
  Loader2,
  MoreHorizontal,
  Play,
  Rocket,
  Save,
  Share2,
  ShieldCheck,
  Zap,
} from "lucide-react";
import Link from "next/link";
import { useRouter, useSearchParams } from "next/navigation";
import { useCallback, useEffect, useMemo, useState } from "react";
import { toast } from "sonner";
import { ExecutionStatusBadge, WorkflowStatusBadge } from "@/components/status-badge";
import { describeError, ErrorState, LoadingState } from "@/components/states";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Switch } from "@/components/ui/switch";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { isApiError } from "@/lib/api/client";
import { isTriggerType, type NodeStatus, type ValidationIssue } from "@/lib/api/types";
import { humanize } from "@/lib/format";
import { usePreferences } from "@/lib/preferences";
import { resetEditorStore, useEditorStore } from "@/stores/editor-store";
import { useLatestExecution } from "@/features/executions/hooks";
import { usePublishWorkflow, useSaveWorkflow, useValidateWorkflow, useWorkflow } from "@/features/workflows/hooks";
import { EditorCanvas } from "./editor-canvas";
import { NodePalette } from "./node-palette";
import { ConflictDialog } from "./panels/conflict-dialog";
import { RunDialog } from "./panels/run-dialog";
import { SharingSheet } from "./panels/sharing-sheet";
import { TriggersSheet } from "./panels/triggers-sheet";
import { ValidationPanel } from "./panels/validation-panel";
import { VersionsSheet } from "./panels/versions-sheet";
import { SidePanel } from "./side-panel";

type Sheet = "versions" | "triggers" | "sharing" | null;

export function WorkflowEditorPage({ workflowId }: { workflowId: string }) {
  const query = useWorkflow(workflowId);
  const loadedId = useEditorStore((s) => s.workflowId);
  const load = useEditorStore((s) => s.load);

  useEffect(() => {
    if (query.data && useEditorStore.getState().workflowId !== query.data.id) load(query.data);
  }, [query.data, load]);
  useEffect(() => () => resetEditorStore(), []);

  if (query.isPending) return <LoadingState label="Loading workflow…" className="h-full" />;
  if (query.isError) {
    return (
      <div className="p-8">
        <ErrorState
          error={query.error}
          title={isApiError(query.error) && query.error.status === 404 ? "Workflow not found" : undefined}
          onRetry={() => void query.refetch()}
        />
        <div className="mt-4 text-center">
          <Button variant="link" asChild>
            <Link href="/workflows">Back to workflows</Link>
          </Button>
        </div>
      </div>
    );
  }
  if (loadedId !== workflowId) return <LoadingState label="Preparing editor…" className="h-full" />;
  return (
    <ReactFlowProvider>
      <EditorWorkspace workflowId={workflowId} />
    </ReactFlowProvider>
  );
}

function EditorWorkspace({ workflowId }: { workflowId: string }) {
  const router = useRouter();
  const searchParams = useSearchParams();
  const query = useWorkflow(workflowId);
  const workflow = query.data;
  const [prefs] = usePreferences();

  const dirty = useEditorStore((s) => s.dirty);
  const readOnly = useEditorStore((s) => s.readOnly);
  const role = useEditorStore((s) => s.role);
  const name = useEditorStore((s) => s.meta.name);
  const setIssues = useEditorStore((s) => s.setIssues);
  const applyLayout = useEditorStore((s) => s.applyLayout);
  const load = useEditorStore((s) => s.load);
  const markSaved = useEditorStore((s) => s.markSaved);
  const triggerType = useEditorStore((s) => s.nodes.find((n) => isTriggerType(n.data.nodeType))?.data.nodeType ?? null);

  const save = useSaveWorkflow(workflowId);
  const validate = useValidateWorkflow(workflowId);
  const publish = usePublishWorkflow(workflowId);

  const [sheet, setSheet] = useState<Sheet>(() => {
    const panel = searchParams.get("panel");
    return panel === "triggers" || panel === "versions" || panel === "sharing" ? panel : null;
  });
  const [runOpen, setRunOpen] = useState(false);
  const [validation, setValidation] = useState<{ valid: boolean; issues: ValidationIssue[] } | null>(null);
  const [conflict, setConflict] = useState<{ correlationId: string | null; currentRevision: number | null } | null>(null);
  const [showLastRun, setShowLastRun] = useState(false);
  const { latest, detail } = useLatestExecution(workflowId, showLastRun);
  const lastRun = showLastRun ? (latest.data === null ? null : detail.data) : undefined;

  const statusById = useMemo(() => {
    if (!showLastRun || !detail.data) return undefined;
    const out: Record<string, NodeStatus> = {};
    for (const n of detail.data.nodes) out[n.nodeId] = n.status;
    return out;
  }, [showLastRun, detail.data]);

  // Warn before closing the tab with unsaved changes.
  useEffect(() => {
    if (!dirty) return;
    const handler = (event: BeforeUnloadEvent) => {
      event.preventDefault();
    };
    window.addEventListener("beforeunload", handler);
    return () => window.removeEventListener("beforeunload", handler);
  }, [dirty]);

  const doSave = useCallback(
    async (overrideRevision?: number): Promise<boolean> => {
      const state = useEditorStore.getState();
      if (state.readOnly) return false;
      const counter = state.changeCounter;
      try {
        const saved = await save.mutateAsync({
          name: state.meta.name,
          description: state.meta.description.trim() ? state.meta.description : null,
          definition: state.toDefinition(),
          revision: overrideRevision ?? state.revision,
        });
        markSaved(saved.revision, counter);
        toast.success("Workflow saved", { description: `Revision ${saved.revision}` });
        return true;
      } catch (error) {
        if (isApiError(error) && error.status === 409) {
          const current = error.extensions.currentRevision;
          setConflict({ correlationId: error.correlationId, currentRevision: typeof current === "number" ? current : null });
        } else if (isApiError(error) && error.status === 422) {
          const issues = error.errors ?? [];
          setIssues(issues);
          setValidation({ valid: false, issues });
          toast.error("The workflow could not be saved", { description: error.detail ?? "Fix the highlighted problems." });
        } else {
          const info = describeError(error);
          toast.error(info.title, { description: `${info.detail}${info.correlationId ? ` (correlation ${info.correlationId})` : ""}` });
        }
        return false;
      }
    },
    [save, markSaved, setIssues],
  );

  const doValidate = async () => {
    try {
      const result = await validate.mutateAsync(useEditorStore.getState().toDefinition());
      setIssues(result.errors);
      setValidation({ valid: result.valid, issues: result.errors });
      if (result.valid) toast.success("No problems found");
    } catch (error) {
      if (isApiError(error) && error.status === 422 && error.errors) {
        setIssues(error.errors);
        setValidation({ valid: false, issues: error.errors });
        return;
      }
      const info = describeError(error);
      toast.error(info.title, { description: info.detail });
    }
  };

  const doPublish = async () => {
    if (useEditorStore.getState().dirty && !(await doSave())) return;
    try {
      const version = await publish.mutateAsync();
      setIssues([]);
      setValidation(null);
      toast.success(`Published version ${version.version}`, { description: "New executions use this version." });
    } catch (error) {
      if (isApiError(error) && error.status === 422) {
        const issues = error.errors ?? [];
        setIssues(issues);
        setValidation({ valid: false, issues });
        toast.error("Publishing failed", { description: error.detail ?? "Fix the problems first." });
        return;
      }
      const info = describeError(error);
      toast.error(info.title, { description: info.detail });
    }
  };

  const reloadLatest = async () => {
    setConflict(null);
    const result = await query.refetch();
    if (result.data) {
      load(result.data);
      toast.info("Loaded the latest revision");
    }
  };

  // Ctrl/Cmd+S saves.
  useEffect(() => {
    const onKey = (event: KeyboardEvent) => {
      if ((event.metaKey || event.ctrlKey) && event.key.toLowerCase() === "s") {
        event.preventDefault();
        if (!useEditorStore.getState().readOnly) void doSave();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [doSave]);

  if (!workflow) return null;
  const canRun = workflow.latestVersion !== null && role !== "VIEWER" && workflow.status !== "ARCHIVED";
  const busy = save.isPending || publish.isPending;

  return (
    <div className="flex h-full flex-col">
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
          <Switch checked={showLastRun} onCheckedChange={setShowLastRun} aria-label="Show last run" />
          <span className="hidden lg:inline">Show last run</span>
        </label>
        {showLastRun && detail.data ? (
          <Link href={`/executions/${detail.data.id}`} className="hidden items-center gap-1 text-xs underline-offset-2 hover:underline md:flex">
            <ExecutionStatusBadge status={detail.data.status} />
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
            <Button variant="outline" size="sm" onClick={doValidate} disabled={validate.isPending} data-testid="validate-button">
              {validate.isPending ? <Loader2 className="animate-spin" /> : <ShieldCheck />} Validate
            </Button>
            <Button variant="outline" size="sm" onClick={() => void doSave()} disabled={busy || !dirty} data-testid="save-button">
              {save.isPending ? <Loader2 className="animate-spin" /> : <Save />} Save
            </Button>
            <Button variant="outline" size="sm" onClick={doPublish} disabled={busy} data-testid="publish-button">
              {publish.isPending ? <Loader2 className="animate-spin" /> : <Rocket />} Publish
            </Button>
          </>
        ) : null}
        <Tooltip>
          <TooltipTrigger asChild>
            <span>
              <Button size="sm" onClick={() => setRunOpen(true)} disabled={!canRun} data-testid="run-button">
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
            <DropdownMenuItem onSelect={() => setSheet("versions")}>
              <History /> Versions
            </DropdownMenuItem>
            <DropdownMenuItem onSelect={() => setSheet("triggers")}>
              <Zap /> Triggers
            </DropdownMenuItem>
            {role === "OWNER" ? (
              <>
                <DropdownMenuSeparator />
                <DropdownMenuItem onSelect={() => setSheet("sharing")}>
                  <Share2 /> Sharing
                </DropdownMenuItem>
              </>
            ) : null}
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
      <div className="grid min-h-0 flex-1 grid-cols-1 grid-rows-[minmax(0,1fr)_auto] md:grid-cols-[14rem_minmax(0,1fr)] lg:grid-cols-[15rem_minmax(0,1fr)_22rem] lg:grid-rows-1">
        <div className="hidden min-h-0 md:row-span-2 md:block lg:row-span-1">
          <NodePalette />
        </div>
        <div className="relative min-h-[20rem]">
          <EditorCanvas statusById={statusById} />
          {validation ? <ValidationPanel valid={validation.valid} issues={validation.issues} onClose={() => setValidation(null)} /> : null}
        </div>
        <div className="max-h-[45vh] min-h-0 border-t md:col-start-2 lg:col-start-3 lg:row-start-1 lg:max-h-none lg:border-t-0">
          <SidePanel lastRun={lastRun} />
        </div>
      </div>
      <RunDialog workflowId={workflowId} version={workflow.latestVersion} triggerType={triggerType} open={runOpen} onOpenChange={setRunOpen} />
      <VersionsSheet workflowId={workflowId} open={sheet === "versions"} onOpenChange={(o) => setSheet(o ? "versions" : null)} />
      <TriggersSheet workflowId={workflowId} role={role} open={sheet === "triggers"} onOpenChange={(o) => setSheet(o ? "triggers" : null)} />
      {role === "OWNER" ? (
        <SharingSheet workflowId={workflowId} open={sheet === "sharing"} onOpenChange={(o) => setSheet(o ? "sharing" : null)} />
      ) : null}
      <ConflictDialog
        open={conflict !== null}
        onOpenChange={(o) => !o && setConflict(null)}
        correlationId={conflict?.correlationId ?? null}
        onReload={() => void reloadLatest()}
        onOverwrite={() => {
          const revision = conflict?.currentRevision;
          setConflict(null);
          if (typeof revision === "number") void doSave(revision);
          else void reloadLatest();
        }}
      />
    </div>
  );
}
