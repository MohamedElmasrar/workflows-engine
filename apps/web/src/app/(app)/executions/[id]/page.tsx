"use client";
/** Route /executions/[id]; remounts the detail page when the id changes. */
import { useParams } from "next/navigation";
import { ExecutionDetailPage } from "@/features/executions/execution-detail-page";

export default function Page() {
  const { id } = useParams<{ id: string }>();
  return <ExecutionDetailPage key={id} executionId={id} />;
}
