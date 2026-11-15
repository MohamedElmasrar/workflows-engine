/** Workflow list URL state: filters, sort, page and the debounced search box. */
import { useEffect, useState } from "react";
import { useDebouncedValue } from "@/lib/hooks";
import { useUrlState } from "@/lib/url-state";
import { type SortField, SORTS } from "../components/sort-header";

export function useWorkflowListParams() {
  const { get, update } = useUrlState();
  const status = get("status");
  const sort: SortField = (SORTS as readonly string[]).includes(get("sort")) ? (get("sort") as SortField) : "updatedAt";
  const direction = get("direction") === "asc" ? "asc" : "desc";
  const page = Math.max(0, Number(get("page")) || 0);
  const urlSearch = get("search");

  const [search, setSearch] = useState(urlSearch);
  const debounced = useDebouncedValue(search, 300);
  useEffect(() => {
    if (debounced !== urlSearch) update({ search: debounced });
  }, [debounced]); // eslint-disable-line react-hooks/exhaustive-deps

  const onSort = (field: SortField) => {
    if (field === sort) update({ direction: direction === "asc" ? "desc" : "asc" });
    else update({ sort: field, direction: field === "name" ? "asc" : "desc" });
  };

  return { update, status, sort, direction, page, urlSearch, search, setSearch, onSort } as const;
}
