import { Loader2, Workflow } from "lucide-react";
import type { ReactNode } from "react";

export function FullPageSpinner({ label }: { label: string }) {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center gap-4 bg-muted/30" role="status" aria-live="polite">
      <div className="flex items-center gap-2 text-lg font-semibold">
        <Workflow className="size-6 text-primary" aria-hidden />
        Workflow Platform
      </div>
      <Loader2 className="size-6 animate-spin text-muted-foreground" aria-hidden />
      <p className="text-sm text-muted-foreground">{label}</p>
    </div>
  );
}

export function FullPageMessage({ title, description, action }: { title: string; description: string; action?: ReactNode }) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-muted/30 p-6">
      <div className="max-w-md space-y-3 rounded-xl border bg-background p-6 text-center shadow-sm" role="alert">
        <h1 className="text-lg font-semibold">{title}</h1>
        <p className="text-sm text-muted-foreground">{description}</p>
        {action}
      </div>
    </div>
  );
}
