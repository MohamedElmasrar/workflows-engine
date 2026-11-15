"use client";
/** Settings page: account, preferences, trigger overview and the credentials notice. */
import { Info } from "lucide-react";
import { PageContainer } from "@/components/layout/app-shell";
import { PageHeader } from "@/components/page-header";
import { Alert, AlertDescription, AlertTitle } from "@/components/ui/alert";
import { PreferencesCard } from "./components/preferences-card";
import { TriggerOverviewCard } from "./components/trigger-overview-card";
import { UserCard } from "./components/user-card";

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
