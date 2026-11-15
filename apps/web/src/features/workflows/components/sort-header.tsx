/** Sortable table column header for the workflow list (name, updated, created). */
import { ArrowDown, ArrowUp, ArrowUpDown } from "lucide-react";
import { TableHead } from "@/components/ui/table";

export const SORTS = ["name", "updatedAt", "createdAt"] as const;
export type SortField = (typeof SORTS)[number];

export function SortHeader({ field, label, sort, direction, onSort }: {
  field: SortField;
  label: string;
  sort: SortField;
  direction: "asc" | "desc";
  onSort: (field: SortField) => void;
}) {
  const active = sort === field;
  const Icon = !active ? ArrowUpDown : direction === "asc" ? ArrowUp : ArrowDown;
  return (
    <TableHead aria-sort={active ? (direction === "asc" ? "ascending" : "descending") : "none"}>
      <button
        type="button"
        onClick={() => onSort(field)}
        className="inline-flex items-center gap-1 rounded hover:text-foreground focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
      >
        {label}
        <Icon className="size-3.5" aria-hidden />
      </button>
    </TableHead>
  );
}
