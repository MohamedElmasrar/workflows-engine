"use client";

import { ChevronDown, ChevronRight } from "lucide-react";
import { useState } from "react";
import { cn } from "@/lib/utils";
import { CopyButton } from "./copy-button";

function isObject(value: unknown): value is Record<string, unknown> {
  return typeof value === "object" && value !== null;
}

function Primitive({ value }: { value: unknown }) {
  if (value === null) return <span className="text-muted-foreground">null</span>;
  if (typeof value === "string") return <span className="break-all text-emerald-700 dark:text-emerald-400">&quot;{value}&quot;</span>;
  if (typeof value === "number") return <span className="text-blue-700 dark:text-blue-400">{value}</span>;
  if (typeof value === "boolean") return <span className="text-purple-700 dark:text-purple-400">{String(value)}</span>;
  return <span>{String(value)}</span>;
}

function JsonNode({ name, value, depth, defaultExpandDepth }: { name?: string; value: unknown; depth: number; defaultExpandDepth: number }) {
  const [open, setOpen] = useState(depth < defaultExpandDepth);
  const label = name !== undefined ? <span className="text-foreground/80">{name}: </span> : null;
  if (!isObject(value)) {
    return (
      <div className="pl-4">
        {label}
        <Primitive value={value} />
      </div>
    );
  }
  const entries = Array.isArray(value) ? value.map((v, i) => [String(i), v] as const) : Object.entries(value);
  const [openBracket, closeBracket] = Array.isArray(value) ? ["[", "]"] : ["{", "}"];
  if (entries.length === 0) {
    return (
      <div className="pl-4">
        {label}
        <span className="text-muted-foreground">{openBracket + closeBracket}</span>
      </div>
    );
  }
  return (
    <div className={depth > 0 ? "pl-4" : undefined}>
      <button
        type="button"
        className="-ml-4 inline-flex items-center rounded hover:bg-muted focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
        onClick={() => setOpen((o) => !o)}
        aria-expanded={open}
      >
        {open ? <ChevronDown className="size-3.5" aria-hidden /> : <ChevronRight className="size-3.5" aria-hidden />}
        {label}
        <span className="text-muted-foreground">
          {openBracket}
          {!open ? ` ${entries.length} ${Array.isArray(value) ? "items" : "keys"} ${closeBracket}` : null}
        </span>
      </button>
      {open ? (
        <>
          {entries.map(([k, v]) => (
            <JsonNode key={k} name={k} value={v} depth={depth + 1} defaultExpandDepth={defaultExpandDepth} />
          ))}
          <div className="text-muted-foreground">{closeBracket}</div>
        </>
      ) : null}
    </div>
  );
}

export function JsonViewer({
  value,
  className,
  defaultExpandDepth = 2,
  emptyLabel = "No data",
}: {
  value: unknown;
  className?: string;
  defaultExpandDepth?: number;
  emptyLabel?: string;
}) {
  if (value === undefined || value === null) {
    return <p className={cn("text-sm text-muted-foreground", className)}>{emptyLabel}</p>;
  }
  return (
    <div className={cn("relative rounded-md border bg-muted/40 p-3 font-mono text-xs leading-5", className)}>
      <div className="absolute top-1 right-1">
        <CopyButton value={JSON.stringify(value, null, 2)} label="Copy JSON" size="icon-xs" />
      </div>
      <div className="overflow-x-auto pl-4">
        <JsonNode value={value} depth={0} defaultExpandDepth={defaultExpandDepth} />
      </div>
    </div>
  );
}
