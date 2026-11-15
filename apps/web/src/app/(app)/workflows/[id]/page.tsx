"use client";
/** Route /workflows/[id]: the workflow editor, remounted when the id changes. */
import { useParams } from "next/navigation";
import { Suspense } from "react";
import { LoadingState } from "@/components/loading-state";
import { WorkflowEditorPage } from "@/features/editor/editor-page";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  return (
    <Suspense fallback={<LoadingState />}>
      <WorkflowEditorPage key={id} workflowId={id} />
    </Suspense>
  );
}
