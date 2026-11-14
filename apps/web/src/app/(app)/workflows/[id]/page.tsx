"use client";

import { useParams } from "next/navigation";
import { Suspense } from "react";
import { LoadingState } from "@/components/states";
import { WorkflowEditorPage } from "@/features/editor/editor-page";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  return (
    <Suspense fallback={<LoadingState />}>
      <WorkflowEditorPage key={id} workflowId={id} />
    </Suspense>
  );
}
