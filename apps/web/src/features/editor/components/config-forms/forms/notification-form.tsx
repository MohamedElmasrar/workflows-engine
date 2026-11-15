"use client";
/** Notification node config: channel, recipient, subject and message. */
import { NOTIFICATION_CHANNELS } from "@/schemas/node-configs";
import { notificationForm, notificationFormSchema } from "@/schemas/node-forms";
import { SelectField } from "../fields/select-field";
import { TemplateField } from "../fields/template-field";
import { useConfigForm } from "../hooks/use-config-form";
import type { TypeFormProps } from "./type-form-props";

export function NotificationForm(props: TypeFormProps) {
  const form = useConfigForm(notificationFormSchema, notificationForm, props);
  return (
    <div className="space-y-4">
      <SelectField
        control={form.control}
        name="channel"
        label="Channel"
        options={NOTIFICATION_CHANNELS.map((c) => ({ value: c, label: c === "EMAIL" ? "E-mail" : "Log" }))}
        disabled={props.disabled}
      />
      <TemplateField form={form} name="recipient" nodeId={props.nodeId} label="Recipient" placeholder="team@example.com" disabled={props.disabled} />
      <TemplateField form={form} name="subject" nodeId={props.nodeId} label="Subject" optional placeholder="Order received" disabled={props.disabled} />
      <TemplateField form={form} name="message" nodeId={props.nodeId} label="Message" multiline disabled={props.disabled} />
    </div>
  );
}
