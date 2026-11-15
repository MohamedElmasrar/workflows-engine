"use client";
/** Manual trigger: nothing to configure, explains where the run input goes. */
import { Info } from "lucide-react";

export function ManualTriggerForm() {
  return (
    <p className="flex gap-2 rounded-md bg-muted/50 p-3 text-xs text-muted-foreground">
      <Info className="size-4 shrink-0" aria-hidden />
      Manual triggers have no configuration. The JSON input you provide in the Run dialog becomes the trigger output,
      available as <code>{"{{ trigger.<field> }}"}</code>.
    </p>
  );
}
