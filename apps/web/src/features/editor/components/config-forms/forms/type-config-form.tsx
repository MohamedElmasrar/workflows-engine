"use client";
/** Picks the config form that matches a node type. */
import type { NodeType } from "@/lib/api/types";
import { ConditionNodeForm } from "./condition-form";
import { DelayNodeForm } from "./delay-form";
import { HttpRequestForm } from "./http-request-form";
import { LogForm } from "./log-form";
import { ManualTriggerForm } from "./manual-trigger-form";
import { NotificationForm } from "./notification-form";
import { ScheduleTriggerForm } from "./schedule-trigger-form";
import { TransformForm } from "./transform-form";
import type { TypeFormProps } from "./type-form-props";
import { WebhookTriggerForm } from "./webhook-trigger-form";

export function TypeConfigForm({ type, ...props }: TypeFormProps & { type: NodeType }) {
  switch (type) {
    case "MANUAL_TRIGGER":
      return <ManualTriggerForm />;
    case "WEBHOOK_TRIGGER":
      return <WebhookTriggerForm {...props} />;
    case "SCHEDULE_TRIGGER":
      return <ScheduleTriggerForm {...props} />;
    case "HTTP_REQUEST":
      return <HttpRequestForm {...props} />;
    case "TRANSFORM":
      return <TransformForm {...props} />;
    case "LOG":
      return <LogForm {...props} />;
    case "NOTIFICATION":
      return <NotificationForm {...props} />;
    case "CONDITION":
      return <ConditionNodeForm {...props} />;
    case "DELAY":
      return <DelayNodeForm {...props} />;
  }
}
