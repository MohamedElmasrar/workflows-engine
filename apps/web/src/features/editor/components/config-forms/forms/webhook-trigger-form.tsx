"use client";
/** Webhook trigger config: signature requirement and required body fields. */
import { webhookForm, webhookFormSchema } from "@/schemas/node-forms";
import { StringListField } from "../fields/string-list-field";
import { SwitchField } from "../fields/switch-field";
import { useConfigForm } from "../hooks/use-config-form";
import type { TypeFormProps } from "./type-form-props";

export function WebhookTriggerForm(props: TypeFormProps) {
  const form = useConfigForm(webhookFormSchema, webhookForm, props);
  return (
    <div className="space-y-4">
      <SwitchField
        control={form.control}
        name="requireSignature"
        label="Require HMAC signature"
        description="Reject deliveries without a valid X-Webhook-Signature header."
        disabled={props.disabled}
      />
      <StringListField
        form={form}
        name="requiredFields"
        label="Required body fields"
        placeholder="orderId"
        addLabel="Add field"
        hint="Deliveries missing any of these top-level fields are rejected with 422."
        disabled={props.disabled}
      />
      <p className="text-xs text-muted-foreground">
        The URL and signing secret are available in the Triggers panel once the workflow is published.
      </p>
    </div>
  );
}
