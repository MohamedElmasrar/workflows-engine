import type { Metadata } from "next";
import { Suspense } from "react";
import { LoadingState } from "@/components/loading-state";
import { ExecutionListPage } from "@/features/executions/execution-list-page";

export const metadata: Metadata = { title: "Executions" };

export default function Page() {
  return (
    <Suspense fallback={<LoadingState />}>
      <ExecutionListPage />
    </Suspense>
  );
}
