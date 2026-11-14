import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/states";
import { WorkflowListPage } from "@/features/workflows/workflow-list-page";

export const metadata: Metadata = { title: "Workflows" };

export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <WorkflowListPage />
    </Suspense>
  );
}
