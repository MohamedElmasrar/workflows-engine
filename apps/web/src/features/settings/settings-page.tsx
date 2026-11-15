"use client";

import { useQuery } from "@tanstack/react-query";
import { CalendarClock, Info, Webhook } from "lucide-react";
import Link from "next/link";
import { useId } from "react";
import { CopyButton } from "@/components/copy-button";
import { PageContainer } from "@/components/layout/app-shell";
import { PageHeader } from "@/components/page-header";
import { EmptyState } from "@/components/empty-state";
import { ErrorState } from "@/components/error-state";
import { LoadingState } from "@/components/loading-state";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from "@/components/ui/card";
import { Select, SelectContent, SelectItem, SelectTrigger, SelectValue } from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import { api } from "@/lib/api/endpoints";
import { queryKeys } from "@/lib/api/query-keys";
import { useAuth } from "@/lib/auth/auth-provider";
import { usePreferences, type Preferences } from "@/lib/preferences";
import { useWorkflowList } from "@/features/workflows/hooks/workflow-queries";

function Row({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="grid grid-cols-1 gap-1 py-2 sm:grid-cols-[10rem_1fr] sm:items-center">
      <dt className="text-sm text-muted-foreground">{label}</dt>
      <dd className="text-sm">{children}</dd>
    </div>
  );
}

function UserCard() {
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
          <Row label="Username">{me.data?.username ?? user.username}</Row>
          <Row label="Name">{user.name ?? "—"}</Row>
          <Row label="E-mail">{user.email ?? "—"}</Row>
          <Row label="Roles">{(me.data?.roles ?? user.roles).join(", ") || "—"}{me.data?.admin ? " (administrator)" : ""}</Row>
          <Row label="User ID">
            <span className="inline-flex items-center gap-1">
              <code className="font-mono text-xs" data-testid="user-id">{userId}</code>
              <CopyButton value={userId} label="Copy user ID" size="icon-xs" />
            </span>
          </Row>
        </dl>
      </CardContent>
    </Card>
  );
}

function PreferencesCard() {
  const [prefs, setPrefs] = usePreferences();
  const directionId = useId();
  const minimapId = useId();
  const pageSizeId = useId();
  return (
    <Card>
      <CardHeader>
        <CardTitle>Preferences</CardTitle>
        <CardDescription>Stored in this browser only (localStorage). They contain no credentials.</CardDescription>
      </CardHeader>
      <CardContent className="space-y-4">
        <div className="flex items-center justify-between gap-4">
          <label htmlFor={directionId} className="text-sm">
            Auto-layout direction
          </label>
          <Select value={prefs.layoutDirection} onValueChange={(v) => setPrefs({ layoutDirection: v as Preferences["layoutDirection"] })}>
            <SelectTrigger id={directionId} className="w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              <SelectItem value="LR">Left to right</SelectItem>
              <SelectItem value="TB">Top to bottom</SelectItem>
            </SelectContent>
          </Select>
        </div>
        <div className="flex items-center justify-between gap-4">
          <label htmlFor={minimapId} className="text-sm">
            Show minimap in graphs
          </label>
          <Switch id={minimapId} checked={prefs.showMinimap} onCheckedChange={(v) => setPrefs({ showMinimap: v })} />
        </div>
        <div className="flex items-center justify-between gap-4">
          <label htmlFor={pageSizeId} className="text-sm">
            Default page size
          </label>
          <Select value={String(prefs.pageSize)} onValueChange={(v) => setPrefs({ pageSize: Number(v) as Preferences["pageSize"] })}>
            <SelectTrigger id={pageSizeId} className="w-44">
              <SelectValue />
            </SelectTrigger>
            <SelectContent>
              {[10, 20, 50, 100].map((n) => (
                <SelectItem key={n} value={String(n)}>
                  {n} rows
                </SelectItem>
              ))}
            </SelectContent>
          </Select>
        </div>
      </CardContent>
    </Card>
  );
}

function TriggerOverviewCard() {
  const list = useWorkflowList({ status: "ACTIVE", size: 100, sort: "name", direction: "asc" });
  const triggered = (list.data?.items ?? []).filter((w) => w.triggerType === "WEBHOOK_TRIGGER" || w.triggerType === "SCHEDULE_TRIGGER");
  return (
    <Card>
      <CardHeader>
        <CardTitle>Trigger configuration</CardTitle>
        <CardDescription>Active workflows started by webhooks or schedules. Open one to see its URL, secret rotation and deliveries.</CardDescription>
      </CardHeader>
      <CardContent>
        {list.isPending ? (
          <LoadingState />
        ) : list.isError ? (
          <ErrorState error={list.error} onRetry={() => void list.refetch()} />
        ) : triggered.length === 0 ? (
          <EmptyState title="No webhook or schedule triggers" description="Publish a workflow with a webhook or schedule trigger to see it here." />
        ) : (
          <ul className="divide-y rounded-md border">
            {triggered.map((w) => (
              <li key={w.id} className="flex items-center justify-between gap-2 px-3 py-2">
                <span className="flex min-w-0 items-center gap-2">
                  {w.triggerType === "WEBHOOK_TRIGGER" ? <Webhook className="size-4 shrink-0" aria-label="Webhook" /> : <CalendarClock className="size-4 shrink-0" aria-label="Schedule" />}
                  <span className="truncate text-sm font-medium">{w.name}</span>
                  <span className="text-xs text-muted-foreground">v{w.latestVersion}</span>
                </span>
                <Button variant="outline" size="sm" asChild>
                  <Link href={`/workflows/${w.id}?panel=triggers`}>Triggers</Link>
                </Button>
              </li>
            ))}
          </ul>
        )}
      </CardContent>
    </Card>
  );
}

export function SettingsPage() {
  return (
    <PageContainer className="max-w-4xl">
      <PageHeader title="Settings" />
      <div className="space-y-6">
        <UserCard />
        <PreferencesCard />
        <TriggerOverviewCard />
        <Alert>
          <Info />
          <AlertTitle>Integration credentials are not implemented yet</AlertTitle>
          <AlertDescription>
            The platform has no credentials store. Values such as API keys that you type into HTTP Request headers are saved as
            part of the workflow definition and are visible to everyone with access to the workflow. Execution inputs and outputs
            mask keys that look sensitive, but the definition itself does not.
          </AlertDescription>
        </Alert>
      </div>
    </PageContainer>
  );
}
