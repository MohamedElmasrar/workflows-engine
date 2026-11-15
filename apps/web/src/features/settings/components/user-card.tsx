"use client";
/** Account card: username, name, e-mail, roles and the copyable user id. */
import { useQuery } from "@tanstack/react-query";
import { CopyButton } from "@/components/copy-button";
import { ErrorState } from "@/components/error-state";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { api } from "@/lib/api/endpoints";
import { queryKeys } from "@/lib/api/query-keys";
import { useAuth } from "@/lib/auth/auth-provider";
import { SettingsRow } from "./settings-row";

export function UserCard() {
  const { user } = useAuth();
  const me = useQuery({ queryKey: queryKeys.me, queryFn: api.me });
  const userId = me.data?.id ?? user.id;
  return (
    <Card>
      <CardHeader>
        <CardTitle>Account</CardTitle>
        <CardDescription>Your identity as seen by the platform. Share your username with owners who want to give you access.</CardDescription>
      </CardHeader>
      <CardContent>
        {me.isError ? <ErrorState error={me.error} onRetry={() => void me.refetch()} className="mb-4" /> : null}
        <dl className="divide-y">
          <SettingsRow label="Username">{me.data?.username ?? user.username}</SettingsRow>
          <SettingsRow label="Name">{user.name ?? "—"}</SettingsRow>
          <SettingsRow label="E-mail">{user.email ?? "—"}</SettingsRow>
          <SettingsRow label="Roles">{(me.data?.roles ?? user.roles).join(", ") || "—"}{me.data?.admin ? " (administrator)" : ""}</SettingsRow>
          <SettingsRow label="User ID">
            <span className="inline-flex items-center gap-1">
              <code className="font-mono text-xs" data-testid="user-id">{userId}</code>
              <CopyButton value={userId} label="Copy user ID" size="icon-xs" />
            </span>
          </SettingsRow>
        </dl>
      </CardContent>
    </Card>
  );
}
