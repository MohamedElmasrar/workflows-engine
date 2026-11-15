"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { Trash2, UserPlus } from "lucide-react";
import { useId, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { toast } from "sonner";
import { z } from "zod";
import { ConfirmDialog } from "@/components/confirm-dialog";
import { RelativeTime } from "@/components/relative-time";
import { describeError } from "@/components/describe-error";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Sheet, SheetContent, SheetDescription, SheetHeader, SheetTitle } from "@/components/ui/sheet";
import { isApiError } from "@/lib/api/api-error";
import { humanize } from "@/lib/format";
import { useGrantPermission, useRevokePermission } from "@/features/workflows/hooks/workflow-mutations";
import { usePermissions } from "@/features/workflows/hooks/workflow-queries";

const shareSchema = z.object({
  username: z.string().trim().min(1, "Username is required").max(100),
  role: z.enum(["EDITOR", "VIEWER"]),
});
type ShareForm = z.infer<typeof shareSchema>;

export function SharingSheet({ workflowId, open, onOpenChange }: { workflowId: string; open: boolean; onOpenChange: (open: boolean) => void }) {
  const permissions = usePermissions(workflowId, open);
  const grant = useGrantPermission(workflowId);
  const revoke = useRevokePermission(workflowId);
  const [removing, setRemoving] = useState<string | null>(null);
  const form = useForm<ShareForm>({ resolver: zodResolver(shareSchema), defaultValues: { username: "", role: "VIEWER" } });
  const usernameId = useId();
  const roleId = useId();

  const submit = form.handleSubmit(async (values) => {
    try {
      await grant.mutateAsync({ username: values.username.trim(), role: values.role });
      toast.success(`Shared with ${values.username} as ${humanize(values.role)}`);
      form.reset({ username: "", role: values.role });
    } catch (e) {
      if (isApiError(e) && (e.status === 404 || e.status === 409 || e.status === 422)) {
        form.setError("username", { message: e.detail ?? "This user cannot be added. They must sign in once first." });
        return;
      }
      const info = describeError(e);
      toast.error(info.title, { description: info.detail });
    }
  });

  const confirmRemove = async () => {
    if (!removing) return;
    try {
      await revoke.mutateAsync(removing);
      toast.success(`Removed access for ${removing}`);
    } catch (e) {
      const info = describeError(e);
      toast.error(info.title, { description: info.detail });
    } finally {
      setRemoving(null);
    }
  };

  const usernameError = form.formState.errors.username?.message;
  return (
    <Sheet open={open} onOpenChange={onOpenChange}>
      <SheetContent className="w-full sm:max-w-md">
        <SheetHeader>
          <SheetTitle>Sharing</SheetTitle>
          <SheetDescription>Editors can change and run the workflow; viewers can only look at it and its executions.</SheetDescription>
        </SheetHeader>
        <div className="flex-1 space-y-6 overflow-auto px-4 pb-6">
          <form onSubmit={submit} className="space-y-2" aria-label="Share workflow">
            <div className="flex items-start gap-2">
              <div className="flex-1 space-y-1">
                <label htmlFor={usernameId} className="sr-only">
                  Username
                </label>
                <Input
                  id={usernameId}
                  placeholder="Username, e.g. alice"
                  {...form.register("username")}
                  aria-invalid={usernameError ? true : undefined}
                  aria-describedby={usernameError ? `${usernameId}-error` : undefined}
                />
                {usernameError ? (
                  <p id={`${usernameId}-error`} className="text-xs text-destructive" role="alert">
                    {usernameError}
                  </p>
                ) : null}
              </div>
              <label htmlFor={roleId} className="sr-only">
                Role
              </label>
              <Controller
                control={form.control}
                name="role"
                render={({ field }) => (
                  <Select value={field.value} onValueChange={field.onChange}>
                    <SelectTrigger id={roleId} className="w-28">
                      <SelectValue />
                    </SelectTrigger>
                    <SelectContent>
                      <SelectItem value="EDITOR">Editor</SelectItem>
                      <SelectItem value="VIEWER">Viewer</SelectItem>
                    </SelectContent>
                  </Select>
                )}
              />
              <Button type="submit" disabled={grant.isPending}>
                <UserPlus /> Share
              </Button>
            </div>
          </form>
          {permissions.isPending ? (
            <LoadingState />
          ) : permissions.isError ? (
            <ErrorState error={permissions.error} onRetry={() => void permissions.refetch()} />
          ) : (
            <ul className="divide-y rounded-md border" aria-label="People with access">
              {permissions.data.map((p) => (
                <li key={p.userId} className="flex items-center justify-between gap-2 px-3 py-2">
                  <div>
                    <p className="text-sm font-medium">{p.username}</p>
                    <p className="text-xs text-muted-foreground">
                      {humanize(p.role)} · since <RelativeTime value={p.grantedAt} />
                    </p>
                  </div>
                  {p.role !== "OWNER" ? (
                    <Button variant="ghost" size="icon-sm" onClick={() => setRemoving(p.username)} aria-label={`Remove access for ${p.username}`}>
                      <Trash2 />
                    </Button>
                  ) : null}
                </li>
              ))}
            </ul>
          )}
        </div>
        <ConfirmDialog
          open={removing !== null}
          onOpenChange={(o) => !o && setRemoving(null)}
          title={`Remove access for ${removing}?`}
          description="They will no longer see this workflow or its executions."
          confirmLabel="Remove"
          destructive
          pending={revoke.isPending}
          onConfirm={confirmRemove}
        />
      </SheetContent>
    </Sheet>
  );
}
