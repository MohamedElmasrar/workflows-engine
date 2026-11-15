/** Per-row menu on the workflow list: edit/view, duplicate and archive (owner only). */
import { Archive, Copy, MoreHorizontal, Pencil } from "lucide-react";
import { useRouter } from "next/navigation";
import { Button } from "@/components/ui/button";
import { DropdownMenu, DropdownMenuContent, DropdownMenuItem, DropdownMenuSeparator, DropdownMenuTrigger } from "@/components/ui/dropdown-menu";
import type { WorkflowSummary } from "@/lib/api/types";

export function WorkflowRowActions({
  workflow: w,
  onDuplicate,
  onArchive,
}: {
  workflow: WorkflowSummary;
  onDuplicate: (workflow: WorkflowSummary) => void;
  onArchive: (workflow: WorkflowSummary) => void;
}) {
  const router = useRouter();
  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild>
        <Button variant="ghost" size="icon-sm" aria-label={`Actions for ${w.name}`}>
          <MoreHorizontal />
        </Button>
      </DropdownMenuTrigger>
      <DropdownMenuContent align="end">
        <DropdownMenuItem onSelect={() => router.push(`/workflows/${w.id}`)}>
          <Pencil /> {w.role === "VIEWER" ? "View" : "Edit"}
        </DropdownMenuItem>
        <DropdownMenuItem onSelect={() => onDuplicate(w)}>
          <Copy /> Duplicate
        </DropdownMenuItem>
        <DropdownMenuSeparator />
        <DropdownMenuItem
          variant="destructive"
          disabled={w.role !== "OWNER" || w.status === "ARCHIVED"}
          onSelect={() => onArchive(w)}
        >
          <Archive /> Archive{w.role !== "OWNER" ? " (owner only)" : ""}
        </DropdownMenuItem>
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
