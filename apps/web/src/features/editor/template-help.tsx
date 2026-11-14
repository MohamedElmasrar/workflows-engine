import { BookOpen } from "lucide-react";

/** Inline explanation of the restricted template language. */
export function TemplateHelp() {
  return (
    <details className="group rounded-md border bg-muted/30 p-3 text-xs">
      <summary className="flex cursor-pointer items-center gap-2 font-medium select-none">
        <BookOpen className="size-3.5" aria-hidden /> Template syntax
      </summary>
      <div className="mt-2 space-y-2 text-muted-foreground">
        <p>String values can reference data with <code>{"{{ path }}"}</code> placeholders, resolved right before the node runs:</p>
        <ul className="list-disc space-y-1 pl-4">
          <li>
            <code>{"{{ trigger.body.orderId }}"}</code> – output of the trigger (webhook body, manual input, schedule payload)
          </li>
          <li>
            <code>{"{{ nodes.<nodeId>.output.<path> }}"}</code> – output of an upstream node, e.g. <code>{"{{ nodes.http_1.output.body.score }}"}</code>
          </li>
          <li>
            <code>{"{{ execution.id }}"}</code>, <code>{"{{ execution.workflowId }}"}</code>, <code>{"{{ execution.workflowVersion }}"}</code>
          </li>
        </ul>
        <p>
          A value that is exactly one placeholder keeps the referenced JSON type; otherwise values are interpolated as text. Missing
          paths resolve to <code>null</code>. Only ancestors of this node can be referenced. Use the <code>{"{ }"}</code> button next to a
          field to insert a reference.
        </p>
      </div>
    </details>
  );
}
