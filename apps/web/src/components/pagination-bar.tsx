"use client";

import { ChevronLeft, ChevronRight } from "lucide-react";
import { Button } from "@/components/ui/button";

export function PaginationBar({
  page,
  size,
  totalItems,
  totalPages,
  onPageChange,
}: {
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  onPageChange: (page: number) => void;
}) {
  if (totalItems === 0) return null;
  const from = page * size + 1;
  const to = Math.min(totalItems, (page + 1) * size);
  return (
    <nav aria-label="Pagination" className="flex items-center justify-between gap-4 px-1 py-3 text-sm text-muted-foreground">
      <p>
        Showing <span className="font-medium text-foreground">{from}</span>–<span className="font-medium text-foreground">{to}</span> of{" "}
        <span className="font-medium text-foreground">{totalItems}</span>
      </p>
      <div className="flex items-center gap-2">
        <Button variant="outline" size="sm" onClick={() => onPageChange(page - 1)} disabled={page <= 0} aria-label="Previous page">
          <ChevronLeft /> Previous
        </Button>
        <span aria-live="polite">
          Page {page + 1} of {Math.max(1, totalPages)}
        </span>
        <Button
          variant="outline"
          size="sm"
          onClick={() => onPageChange(page + 1)}
          disabled={page + 1 >= totalPages}
          aria-label="Next page"
        >
          Next <ChevronRight />
        </Button>
      </div>
    </nav>
  );
}
