"use client";

import { useReactFlow } from "@xyflow/react";
import { Repeat } from "lucide-react";
import type { DragEvent } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";
import { Tooltip, TooltipContent, TooltipTrigger } from "@/components/ui/tooltip";
import { isTriggerType, type NodeType } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { useEditorStore } from "@/stores/editor-store";
import { DEFAULT_NODE_HEIGHT, DEFAULT_NODE_WIDTH } from "./auto-layout";
import { DND_MIME } from "./editor-canvas";
import type { WorkflowFlowNode } from "./flow-types";
import { NODE_CATALOG, PALETTE_GROUPS } from "./node-catalog";

/** Moves a candidate position down until it no longer overlaps an existing node. */
export function findFreeSpot(start: { x: number; y: number }, nodes: WorkflowFlowNode[]): { x: number; y: number } {
  const pos = { ...start };
  const overlaps = () =>
    nodes.some((n) => {
      const w = n.measured?.width ?? DEFAULT_NODE_WIDTH;
      const h = n.measured?.height ?? DEFAULT_NODE_HEIGHT;
      return pos.x < n.position.x + w + 20 && pos.x + DEFAULT_NODE_WIDTH + 20 > n.position.x && pos.y < n.position.y + h + 20 && pos.y + DEFAULT_NODE_HEIGHT + 20 > n.position.y;
    });
  for (let i = 0; i < 50 && overlaps(); i++) pos.y += DEFAULT_NODE_HEIGHT + 30;
  return pos;
}

export function NodePalette() {
  const readOnly = useEditorStore((s) => s.readOnly);
  const triggerType = useEditorStore((s) => s.nodes.find((n) => isTriggerType(n.data.nodeType))?.data.nodeType ?? null);
  const addNode = useEditorStore((s) => s.addNode);
  const replaceTrigger = useEditorStore((s) => s.replaceTrigger);
  const { screenToFlowPosition } = useReactFlow();

  const addAtCenter = (type: NodeType) => {
    const canvas = document.querySelector<HTMLElement>("[data-testid='editor-canvas']");
    const rect = canvas?.getBoundingClientRect();
    const center = rect
      ? screenToFlowPosition({ x: rect.left + rect.width / 2, y: rect.top + rect.height / 2 })
      : { x: 0, y: 0 };
    const id = addNode(type, findFreeSpot({ x: center.x - DEFAULT_NODE_WIDTH / 2, y: center.y - DEFAULT_NODE_HEIGHT / 2 }, useEditorStore.getState().nodes));
    if (id) toast.success(`Added ${NODE_CATALOG[type].label}`, { description: `Node id: ${id}`, duration: 1500 });
  };

  const onDragStart = (event: DragEvent, type: NodeType) => {
    event.dataTransfer.setData(DND_MIME, type);
    event.dataTransfer.effectAllowed = "move";
  };

  return (
    <aside aria-label="Node palette" className="flex h-full flex-col gap-4 overflow-y-auto border-r bg-muted/20 p-3">
      <p className="text-xs text-muted-foreground">{readOnly ? "Read-only: you can view but not edit this workflow." : "Click or drag a node onto the canvas."}</p>
      {PALETTE_GROUPS.map((group) => (
        <section key={group.title} aria-labelledby={`palette-${group.category}`} className="space-y-1.5">
          <h2 id={`palette-${group.category}`} className="px-1 text-xs font-semibold tracking-wide text-muted-foreground uppercase">
            {group.title}
          </h2>
          <ul className="space-y-1">
            {group.types.map((type) => {
              const entry = NODE_CATALOG[type];
              const Icon = entry.icon;
              const blockedTrigger = isTriggerType(type) && triggerType !== null;
              const disabled = readOnly || blockedTrigger;
              return (
                <li key={type} className="group relative">
                  <button
                    type="button"
                    draggable={!disabled}
                    onDragStart={(e) => onDragStart(e, type)}
                    onClick={() => addAtCenter(type)}
                    disabled={disabled}
                    data-testid={`palette-${type}`}
                    aria-label={`Add ${entry.label}${blockedTrigger ? " (workflow already has a trigger)" : ""}`}
                    className={cn(
                      "flex w-full items-start gap-2.5 rounded-md border bg-background p-2 text-left transition-colors hover:border-primary/50 hover:bg-muted/50 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none disabled:cursor-not-allowed disabled:opacity-50",
                      !disabled && "cursor-grab active:cursor-grabbing",
                    )}
                  >
                    <span
                      className={cn(
                        "mt-0.5 flex size-7 shrink-0 items-center justify-center rounded-md",
                        entry.category === "trigger"
                          ? "bg-violet-600 text-white"
                          : entry.category === "logic"
                            ? "bg-amber-500 text-white"
                            : "bg-sky-100 text-sky-700 dark:bg-sky-900 dark:text-sky-200",
                      )}
                      aria-hidden
                    >
                      <Icon className="size-3.5" />
                    </span>
                    <span className="min-w-0">
                      <span className="block text-sm font-medium">{entry.label}</span>
                      <span className="block text-xs leading-snug text-muted-foreground">{entry.description}</span>
                    </span>
                  </button>
                  {blockedTrigger && !readOnly && triggerType !== type ? (
                    <Tooltip>
                      <TooltipTrigger asChild>
                        <Button
                          type="button"
                          size="icon-xs"
                          variant="outline"
                          className="absolute top-1.5 right-1.5"
                          aria-label={`Replace trigger with ${entry.label}`}
                          onClick={() => {
                            if (replaceTrigger(type)) toast.success(`Trigger replaced with ${entry.label}`);
                          }}
                        >
                          <Repeat />
                        </Button>
                      </TooltipTrigger>
                      <TooltipContent>Replace the current trigger</TooltipContent>
                    </Tooltip>
                  ) : null}
                </li>
              );
            })}
          </ul>
        </section>
      ))}
    </aside>
  );
}
