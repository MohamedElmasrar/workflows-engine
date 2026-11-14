"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, Plus } from "lucide-react";
import { useRouter } from "next/navigation";
import { useId, useState } from "react";
import { useForm } from "react-hook-form";
import { toast } from "sonner";
import { CorrelationId, describeError } from "@/components/states";
import { Button } from "@/components/ui/button";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle, DialogTrigger } from "@/components/ui/dialog";
import { Input } from "@/components/ui/input";
import { Textarea } from "@/components/ui/textarea";
import { createWorkflowSchema, type CreateWorkflowForm } from "@/schemas/node-forms";
import { useCreateWorkflow } from "./hooks";

export function CreateWorkflowDialog() {
  const [open, setOpen] = useState(false);
  const router = useRouter();
  const create = useCreateWorkflow();
  const form = useForm<CreateWorkflowForm>({ resolver: zodResolver(createWorkflowSchema), defaultValues: { name: "", description: "" } });
  const nameId = useId();
  const descriptionId = useId();
  const { errors } = form.formState;

  const submit = form.handleSubmit(async (values) => {
    try {
      const workflow = await create.mutateAsync({
        name: values.name.trim(),
        ...(values.description.trim() ? { description: values.description.trim() } : {}),
      });
      toast.success("Workflow created");
      setOpen(false);
      form.reset();
      router.push(`/workflows/${workflow.id}`);
    } catch {
      // Error is rendered inline below.
    }
  });

  const failure = create.error ? describeError(create.error) : null;
  return (
    <Dialog
      open={open}
      onOpenChange={(next) => {
        setOpen(next);
        if (!next) create.reset();
      }}
    >
      <DialogTrigger asChild>
        <Button data-testid="create-workflow">
          <Plus /> New workflow
        </Button>
      </DialogTrigger>
      <DialogContent>
        <form onSubmit={submit} className="space-y-4" noValidate>
          <DialogHeader>
            <DialogTitle>Create workflow</DialogTitle>
            <DialogDescription>It starts with a manual trigger; you can change it in the editor.</DialogDescription>
          </DialogHeader>
          <div className="space-y-1.5">
            <label htmlFor={nameId} className="text-sm font-medium">
              Name
            </label>
            <Input
              id={nameId}
              autoFocus
              {...form.register("name")}
              aria-invalid={errors.name ? true : undefined}
              aria-describedby={errors.name ? `${nameId}-error` : undefined}
            />
            {errors.name ? (
              <p id={`${nameId}-error`} className="text-xs text-destructive" role="alert">
                {errors.name.message}
              </p>
            ) : null}
          </div>
          <div className="space-y-1.5">
            <label htmlFor={descriptionId} className="text-sm font-medium">
              Description <span className="font-normal text-muted-foreground">(optional)</span>
            </label>
            <Textarea
              id={descriptionId}
              rows={3}
              {...form.register("description")}
              aria-invalid={errors.description ? true : undefined}
              aria-describedby={errors.description ? `${descriptionId}-error` : undefined}
            />
            {errors.description ? (
              <p id={`${descriptionId}-error`} className="text-xs text-destructive" role="alert">
                {errors.description.message}
              </p>
            ) : null}
          </div>
          {failure ? (
            <div role="alert" className="rounded-md border border-destructive/30 bg-destructive/5 p-3 text-sm">
              <p className="font-medium">{failure.title}</p>
              <p className="text-muted-foreground">{failure.detail}</p>
              <CorrelationId id={failure.correlationId} />
            </div>
          ) : null}
          <DialogFooter>
            <Button type="button" variant="outline" onClick={() => setOpen(false)}>
              Cancel
            </Button>
            <Button type="submit" disabled={create.isPending}>
              {create.isPending ? <Loader2 className="animate-spin" /> : null} Create and open editor
            </Button>
          </DialogFooter>
        </form>
      </DialogContent>
    </Dialog>
  );
}
