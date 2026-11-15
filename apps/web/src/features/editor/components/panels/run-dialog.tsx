"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Play } from "lucide-react";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { CorrelationId } from "@/components/correlation-id";
import { describeError } from "@/components/describe-error";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Textarea } from "@/components/ui/textarea";
import { newCorrelationId } from "@/lib/api/client";
import { useStartExecution } from "@/features/workflows/hooks";

const runSchema = z.object({
  input: z.string().refine((text) => {
    if (text.trim() === "") return true;
    try {
      const v: unknown = JSON.parse(text);
      return typeof v === "object" && v !== null && !Array.isArray(v);
    } catch {
      return false;
    }
  }, "Input must be a JSON object"),
});
type RunForm = z.infer<typeof runSchema>;

export function RunDialog({
  workflowId,
  version,
  triggerType,
  open,
  onOpenChange,
}: {
  workflowId: string;
  version: number | null;
  triggerType: string | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
}) {
  const router = useRouter();
  const start = useStartExecution(workflowId);
  const id = useId();
  // One idempotency key per dialog opening, so a double click cannot start two runs.
  const [idempotencyKey, setIdempotencyKey] = useState(newCorrelationId);
  const form = useForm<RunForm>({ resolver: zodResolver(runSchema), defaultValues: { input: "{\n  \n}" } });
  const error = form.formState.errors.input?.message;

  const submit = form.handleSubmit(async ({ input }) => {
    try {
      const execution = await start.mutateAsync({ input: input.trim() ? (JSON.parse(input) as unknown) : {}, idempotencyKey });
      toast.success("Execution started", { description: `Version ${execution.workflowVersion}` });
      onOpenChange(false);
      setIdempotencyKey(newCorrelationId());
      router.push(`/executions/${execution.id}`);
    } catch (e) {
      const info = describeError(e);
      toast.error(info.title, { description: info.detail });
    }
  });

  const failure = start.error ? describeError(start.error) : null;
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        if (!next) {
          start.reset();
          setIdempotencyKey(newCorrelationId());
        }
        onOpenChange(next);
      }}
    >
      <DialogContent className="sm:max-w-lg">
        <form onSubmit={submit} className="space-y-4">
          <DialogHeader>
            <DialogTitle>Run workflow</DialogTitle>
            <DialogDescription>
              Starts the latest published version{version ? ` (v${version})` : ""}. The input becomes the trigger output
              {triggerType && triggerType !== "MANUAL_TRIGGER" ? " (simulating the configured trigger)" : ""}, e.g.{" "}
              <code>{"{{ trigger.orderId }}"}</code>.
            </DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <label htmlFor={id} className="text-sm font-medium">
              Input (JSON object)
            </label>
            <Textarea
              id={id}
              {...form.register("input")}
              rows={8}
              spellCheck={false}
              className="font-mono text-xs"
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? `${id}-error` : undefined}
              data-testid="run-input"
            />
            {error ? (
              <p id={`${id}-error`} className="text-xs text-destructive" role="alert">
                {error}
              </p>
            ) : null}
          </div>
          {failure ? (
            <div className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm" role="alert">
              <p className="font-medium">{failure.title}</p>
              <p className="text-muted-foreground">{failure.detail}</p>
              <CorrelationId id={failure.correlationId} />
            </div>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => onOpenChange(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={start.isPending} data-testid="run-submit">
              {start.isPending ? <Loader2 className="animate-spin" /> : <Play />}
              {start.isPending ? "Starting…" : "Run"}
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
