"use client";
/** Editor route content: loads the workflow into the editor store, then shows the workspace. */
import { ReactFlowProvider } from "@xyflow/react";
import Link from "next/link";
import { useEffect } from "react";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { Button } from "@/components/ui/button";
import { useWorkflow } from "@/features/workflows/hooks";
import { isApiError } from "@/lib/api/api-error";
import { resetEditorStore, useEditorStore } from "@/stores/editor/editor-store";
import { EditorWorkspace } from "./components/editor-workspace";

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
