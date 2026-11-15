"use client";
/** Popover listing the template references (`{{ ... }}`) available to the current node. */
import { Braces } from "lucide-react";
import { useState } from "react";
import { Button } from "@/components/ui/button";
import { Popover, PopoverContent, PopoverTrigger } from "@/components/ui/popover";
import { useTemplateReferences } from "../../../hooks/use-template-references";

export function ReferencePicker({ nodeId, onInsert, disabled }: { nodeId: string; onInsert: (text: string) => void; disabled?: boolean }) {
  const references = useTemplateReferences(nodeId);
  const [open, setOpen] = useState(false);
  return (
    <Popover open={open} onOpenChange={setOpen}>
      <PopoverTrigger asChild>
        <Button type="button" variant="ghost" size="icon-xs" aria-label="Insert reference" title="Insert reference" disabled={disabled}>
          <Braces />
        </Button>
      </PopoverTrigger>
      <PopoverContent align="end" className="w-80 p-2">
        <p className="px-2 pb-1 text-xs font-medium text-muted-foreground">Insert reference</p>
        <ul className="max-h-72 overflow-auto" role="listbox" aria-label="Template references">
          {references.map((ref) => (
            <li key={ref.value}>
              <button
                type="button"
                role="option"
                aria-selected={false}
                className="flex w-full flex-col items-start rounded-md px-2 py-1.5 text-left hover:bg-muted focus-visible:bg-muted focus-visible:outline-none"
                onClick={() => {
                  onInsert(ref.value);
                  setOpen(false);
                }}
              >
                <code className="text-xs">{ref.value}</code>
                <span className="text-xs text-muted-foreground">{ref.label}</span>
              </button>
            </li>
          ))}
        </ul>
      </PopoverContent>
    </Popover>
  );
}
