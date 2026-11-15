"use client";
/** Icon button that copies a value to the clipboard and briefly shows a check mark. */
import { Check, Copy } from "lucide-react";
import { useState } from "react";
import { toast } from "sonner";
import { Button } from "@/components/ui/button";

export function CopyButton({
  value,
  label = "Copy",
  size = "icon-sm",
  variant = "ghost",
  showLabel = false,
}: {
  value: string;
  label?: string;
  size?: "icon-xs" | "icon-sm" | "icon" | "sm";
  variant?: "ghost" | "outline";
  showLabel?: boolean;
}) {
  const [copied, setCopied] = useState(false);
  const copy = async () => {
    try {
      await navigator.clipboard.writeText(value);
      setCopied(true);
      setTimeout(() => setCopied(false), 1500);
    } catch {
      toast.error("Could not copy to the clipboard");
    }
  };
  return (
    <Button type="button" variant={variant} size={showLabel ? "sm" : size} onClick={copy} aria-label={label} title={label}>
      {copied ? <Check className="text-emerald-600" /> : <Copy />}
      {showLabel ? (copied ? "Copied" : label) : null}
    </Button>
  );
}
