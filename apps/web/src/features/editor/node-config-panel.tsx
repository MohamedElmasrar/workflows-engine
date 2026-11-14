"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { ChevronDown, Trash2 } from "lucide-react";
import { useState } from "react";
import { Controller, useForm, useWatch } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Collapsible, CollapsibleContent, CollapsibleTrigger } from "@/components/ui/collapsible";
import { Switch } from "@/components/ui/switch";
import { isTriggerType } from "@/lib/api/types";
import { cn } from "@/lib/utils";
import { basicsFromForm, basicsToForm, nodeBasicsFormSchema, type NodeBasicsForm } from "@/schemas/node-forms";
import { useEditorStore } from "@/stores/editor-store";
import { InputField, useLiveSync } from "./config-forms/form-fields";
import { TypeConfigForm } from "./config-forms/type-forms";
import type { WorkflowFlowNode } from "./flow-types";
import { NODE_CATALOG } from "./node-catalog";
import { TemplateHelp } from "./template-help";

function NodeBasics({ node, disabled }: { node: WorkflowFlowNode; disabled: boolean }) {
  const updateNode = useEditorStore((s) => s.updateNode);
  const [defaultValues] = useState(() => basicsToForm(node.data));
  const form = useForm<NodeBasicsForm>({ resolver: zodResolver(nodeBasicsFormSchema), defaultValues, mode: "onChange" });
  useLiveSync(form, (values) => updateNode(node.id, basicsFromForm(values)));
  const retryEnabled = useWatch({ control: form.control, name: "retryEnabled" });
  const isTrigger = isTriggerType(node.data.nodeType);
  const [advancedOpen, setAdvancedOpen] = useState(node.data.timeoutSeconds !== undefined || node.data.retryPolicy !== undefined);
  return (
    <div className="space-y-4">
      <InputField form={form} name="name" label="Name" disabled={disabled} />
      {!isTrigger ? (
        <Collapsible open={advancedOpen} onOpenChange={setAdvancedOpen} className="rounded-md border">
          <CollapsibleTrigger asChild>
            <button
              type="button"
              className="flex w-full items-center justify-between px-3 py-2 text-xs font-medium hover:bg-muted/50 focus-visible:ring-2 focus-visible:ring-ring focus-visible:outline-none"
            >
              Advanced: timeout &amp; retries
              <ChevronDown className={cn("size-4 transition-transform", advancedOpen && "rotate-180")} aria-hidden />
            </button>
          </CollapsibleTrigger>
          <CollapsibleContent className="space-y-3 border-t p-3">
            <InputField form={form} name="timeoutSeconds" label="Timeout (seconds)" type="number" numeric optional placeholder="30" hint="1–300, default 30." disabled={disabled} />
            <Controller
              control={form.control}
              name="retryEnabled"
              render={({ field }) => (
                <div className="flex items-center justify-between gap-2">
                  <label htmlFor={`retry-${node.id}`} className="text-xs font-medium">
                    Custom retry policy
                  </label>
                  <Switch id={`retry-${node.id}`} checked={field.value} onCheckedChange={field.onChange} disabled={disabled} />
                </div>
              )}
            />
            {!retryEnabled ? (
              <p className="text-xs text-muted-foreground">Default: 3 attempts, 1 s initial interval, ×2, max 60 s. Only retryable errors are retried.</p>
            ) : (
              <div className="grid grid-cols-2 gap-3">
                <InputField form={form} name="maxAttempts" label="Max attempts" type="number" numeric placeholder="3" disabled={disabled} hint="1–10" />
                <InputField form={form} name="initialIntervalMs" label="Initial interval (ms)" type="number" numeric optional placeholder="1000" disabled={disabled} />
                <InputField form={form} name="multiplier" label="Multiplier" type="number" numeric optional step="0.1" placeholder="2" disabled={disabled} />
                <InputField form={form} name="maxIntervalMs" label="Max interval (ms)" type="number" numeric optional placeholder="60000" disabled={disabled} />
              </div>
            )}
          </CollapsibleContent>
        </Collapsible>
      ) : null}
    </div>
  );
}

export function NodeConfigPanel({ node, readOnly }: { node: WorkflowFlowNode; readOnly: boolean }) {
  const updateNode = useEditorStore((s) => s.updateNode);
  const deleteNode = useEditorStore((s) => s.deleteNode);
  const issues = useEditorStore((s) => s.issues);
  const nodeIssues = issues.filter((i) => i.nodeId === node.id);
  const entry = NODE_CATALOG[node.data.nodeType];
  const Icon = entry.icon;
  return (
    <div className="space-y-5" data-testid="node-config-panel">
      <div className="flex items-start justify-between gap-2">
        <div className="flex items-center gap-2">
          <span className="flex size-8 items-center justify-center rounded-md bg-muted" aria-hidden>
            <Icon className="size-4" />
          </span>
          <div>
            <p className="text-sm font-semibold">{entry.label}</p>
            <p className="font-mono text-xs text-muted-foreground">id: {node.id}</p>
          </div>
        </div>
        {!readOnly ? (
          <Button variant="ghost" size="sm" onClick={() => deleteNode(node.id)} aria-label={`Delete node ${node.data.name}`}>
            <Trash2 /> Delete
          </Button>
        ) : null}
      </div>
      {nodeIssues.length > 0 ? (
        <ul className="space-y-1 rounded-md border border-destructive/30 bg-destructive/5 p-3 text-xs text-destructive" aria-label="Validation errors for this node">
          {nodeIssues.map((issue, i) => (
            <li key={i}>
              <span className="font-mono font-medium">{issue.code}</span>: {issue.message}
            </li>
          ))}
        </ul>
      ) : null}
      <fieldset disabled={readOnly} className="space-y-5">
        <NodeBasics node={node} disabled={readOnly} />
        <div className="space-y-3">
          <h3 className="text-xs font-semibold tracking-wide text-muted-foreground uppercase">Configuration</h3>
          <TypeConfigForm
            type={node.data.nodeType}
            nodeId={node.id}
            config={node.data.config}
            onChange={(config) => updateNode(node.id, { config })}
            disabled={readOnly}
          />
        </div>
      </fieldset>
      {!isTriggerType(node.data.nodeType) ? <TemplateHelp /> : null}
    </div>
  );
}
