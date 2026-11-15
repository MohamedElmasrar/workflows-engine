"use client";
/** Editor layout once a workflow is loaded: toolbar, palette, canvas, side panel, and the dialogs/sheets. */
import { useState } from "react";
import { useSearchParams } from "next/navigation";
import { isTriggerType } from "@/lib/api/types";
import { useEditorStore } from "@/stores/editor/editor-store";
import { useWorkflow } from "@/features/workflows/hooks";
import { useEditorActions } from "../hooks/use-editor-actions";
import { useEditorShortcuts } from "../hooks/use-editor-shortcuts";
import { useLastRun } from "../hooks/use-last-run";
import { EditorCanvas } from "./editor-canvas";
import { NodePalette } from "./node-palette";
import { ConflictDialog } from "./panels/conflict-dialog";
import { RunDialog } from "./panels/run-dialog";
import { SharingSheet } from "./panels/sharing-sheet";
import { TriggersSheet } from "./panels/triggers-sheet";
import { ValidationPanel } from "./panels/validation-panel";
import { VersionsSheet } from "./panels/versions-sheet";
import { SidePanel } from "./side-panel";
import { EditorToolbar, type EditorSheet } from "./editor-toolbar";

function initialSheet(panel: string | null): EditorSheet | null {
  return panel === "triggers" || panel === "versions" || panel === "sharing" ? panel : null;
}

export function EditorWorkspace({ workflowId }: { workflowId: string }) {
  const searchParams = useSearchParams();
  const workflow = useWorkflow(workflowId).data;
  const role = useEditorStore((s) => s.role);
  const triggerType = useEditorStore((s) => s.nodes.find((n) => isTriggerType(n.data.nodeType))?.data.nodeType ?? null);

  const actions = useEditorActions(workflowId);
  const lastRun = useLastRun(workflowId);
  useEditorShortcuts(actions.save);

  const [sheet, setSheet] = useState<EditorSheet | null>(() => initialSheet(searchParams.get("panel")));
  const [runOpen, setRunOpen] = useState(false);
  const sheetProps = (name: EditorSheet) => ({ open: sheet === name, onOpenChange: (o: boolean) => setSheet(o ? name : null) });

  if (!workflow) return null;
  return (
    <div className="flex h-full flex-col">
      <EditorToolbar workflow={workflow} actions={actions} lastRun={lastRun} onRun={() => setRunOpen(true)} onOpenSheet={setSheet} />
      <div className="grid min-h-0 flex-1 grid-cols-1 grid-rows-[minmax(0,1fr)_auto] md:grid-cols-[14rem_minmax(0,1fr)] lg:grid-cols-[15rem_minmax(0,1fr)_22rem] lg:grid-rows-1">
        <div className="hidden min-h-0 md:row-span-2 md:block lg:row-span-1">
          <NodePalette />
        </div>
        <div className="relative min-h-[20rem]">
          <EditorCanvas statusById={lastRun.statusById} />
          {actions.validation ? (
            <ValidationPanel valid={actions.validation.valid} issues={actions.validation.issues} onClose={actions.closeValidation} />
          ) : null}
        </div>
        <div className="max-h-[45vh] min-h-0 border-t md:col-start-2 lg:col-start-3 lg:row-start-1 lg:max-h-none lg:border-t-0">
          <SidePanel lastRun={lastRun.execution} />
        </div>
      </div>
      <RunDialog workflowId={workflowId} version={workflow.latestVersion} triggerType={triggerType} open={runOpen} onOpenChange={setRunOpen} />
      <VersionsSheet workflowId={workflowId} {...sheetProps("versions")} />
      <TriggersSheet workflowId={workflowId} role={role} {...sheetProps("triggers")} />
      {role === "OWNER" ? <SharingSheet workflowId={workflowId} {...sheetProps("sharing")} /> : null}
      <ConflictDialog
        open={actions.conflict !== null}
        onOpenChange={(o) => !o && actions.closeConflict()}
        correlationId={actions.conflict?.correlationId ?? null}
        onReload={() => void actions.reloadLatest()}
        onOverwrite={actions.overwrite}
      />
    </div>
  );
}
