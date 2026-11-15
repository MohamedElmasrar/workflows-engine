/** Tests for the node config panel: stored values, validation, live sync, references and read-only mode. */
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it } from "vitest";
import { TooltipProvider } from "@/components/ui/tooltip";
import type { Workflow } from "@/lib/api/types";
import { resetEditorStore, useEditorStore } from "@/stores/editor/editor-store";
import { NodeConfigPanel } from "./node-config-panel";

const workflow: Workflow = {
  id: "wf",
  name: "Panel test",
  description: null,
  status: "DRAFT",
  ownerId: "u",
  role: "OWNER",
  revision: 1,
  latestVersion: null,
  hasUnpublishedChanges: true,
  definition: {
    schemaVersion: 1,
    nodes: [
      { id: "trigger", type: "MANUAL_TRIGGER", name: "Start", position: { x: 0, y: 0 }, config: {} },
      { id: "log_1", type: "LOG", name: "Log it", position: { x: 300, y: 0 }, config: { message: "hello" } },
      { id: "http_1", type: "HTTP_REQUEST", name: "Call", position: { x: 600, y: 0 }, config: { method: "GET", url: "https://x" } },
    ],
    edges: [
      { id: "e_1", source: "trigger", target: "log_1" },
      { id: "e_2", source: "log_1", target: "http_1" },
    ],
  },
  createdAt: "2026-01-01T00:00:00Z",
  updatedAt: "2026-01-01T00:00:00Z",
};

function renderPanel(nodeId: string, readOnly = false) {
  const node = useEditorStore.getState().nodes.find((n) => n.id === nodeId);
  if (!node) throw new Error("missing node");
  return render(
    <TooltipProvider>
      <NodeConfigPanel node={node} readOnly={readOnly} />
    </TooltipProvider>,
  );
}

const nodeData = (id: string) => useEditorStore.getState().nodes.find((n) => n.id === id)?.data;

describe("NodeConfigPanel", () => {
  beforeEach(() => {
    resetEditorStore();
    useEditorStore.getState().load(workflow);
  });

  it("renders the LOG form with the stored values", () => {
    renderPanel("log_1");
    expect(screen.getByLabelText("Name")).toHaveValue("Log it");
    expect(screen.getByLabelText("Message")).toHaveValue("hello");
    expect(screen.getByText("Template syntax")).toBeInTheDocument();
  });

  it("shows validation errors and does not write invalid config", async () => {
    const user = userEvent.setup();
    renderPanel("log_1");
    await user.clear(screen.getByLabelText("Message"));
    expect(await screen.findByText("Message is required")).toBeInTheDocument();
    expect(nodeData("log_1")?.config).toEqual({ message: "hello" });
    expect(screen.getByLabelText("Message")).toHaveAttribute("aria-invalid", "true");
  });

  it("writes valid edits to the store (live sync) and marks the draft dirty", async () => {
    const user = userEvent.setup();
    renderPanel("log_1");
    const message = screen.getByLabelText("Message");
    await user.clear(message);
    await user.type(message, "Order processed");
    await waitFor(() => expect(nodeData("log_1")?.config).toEqual({ message: "Order processed" }));
    expect(useEditorStore.getState().dirty).toBe(true);
  });

  it("validates the advanced retry policy and timeout", async () => {
    const user = userEvent.setup();
    renderPanel("http_1");
    await user.click(screen.getByRole("button", { name: /advanced/i }));
    await user.type(screen.getByLabelText(/timeout \(seconds\)/i), "500");
    expect(await screen.findByText("Timeout must be at most 300")).toBeInTheDocument();
    await user.clear(screen.getByLabelText(/timeout \(seconds\)/i));
    await user.click(screen.getByRole("switch", { name: /custom retry policy/i }));
    expect(await screen.findByText("Max attempts is required")).toBeInTheDocument();
    await user.type(screen.getByLabelText("Max attempts"), "2");
    await user.type(screen.getByLabelText(/initial interval/i), "500");
    await waitFor(() => expect(nodeData("http_1")?.retryPolicy).toEqual({ maxAttempts: 2, initialIntervalMs: 500 }));
    expect(nodeData("http_1")?.timeoutSeconds).toBeUndefined();
  });

  it("offers upstream references in the insert helper", async () => {
    const user = userEvent.setup();
    renderPanel("http_1");
    await user.click(screen.getAllByRole("button", { name: "Insert reference" })[0]);
    const list = await screen.findByRole("listbox", { name: "Template references" });
    expect(within(list).getByText("{{ nodes.log_1.output }}")).toBeInTheDocument();
    expect(within(list).getByText("{{ trigger }}")).toBeInTheDocument();
    expect(within(list).queryByText("{{ nodes.http_1.output }}")).not.toBeInTheDocument();
    await user.click(within(list).getByText("{{ execution.id }}"));
    await waitFor(() => expect(String(nodeData("http_1")?.config.url)).toContain("{{ execution.id }}"));
  });

  it("disables inputs when read-only", () => {
    renderPanel("log_1", true);
    expect(screen.getByLabelText("Message")).toBeDisabled();
    expect(screen.queryByRole("button", { name: /delete node/i })).not.toBeInTheDocument();
  });
});
