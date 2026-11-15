/** Tests for the editor store: loading, editing, graph rules, dirty tracking, read-only mode and version restore. */
import { beforeEach, describe, expect, it } from "vitest";
import type { Workflow } from "@/lib/api/types";
import { loadDemoDefinition } from "@/test/fixtures";
import { resetEditorStore, useEditorStore } from "./editor-store";
import { nextId } from "./state-helpers";

function workflow(overrides: Partial<Workflow> = {}): Workflow {
  return {
    id: "wf-1",
    name: "Test",
    description: null,
    status: "DRAFT",
    ownerId: "u1",
    role: "OWNER",
    revision: 1,
    latestVersion: null,
    hasUnpublishedChanges: true,
    definition: {
      schemaVersion: 1,
      nodes: [{ id: "trigger", type: "MANUAL_TRIGGER", name: "Manual trigger", position: { x: 0, y: 0 }, config: {} }],
      edges: [],
      settings: { executionTimeoutSeconds: 3600, maxConcurrentExecutions: 10 },
    },
    createdAt: "2026-01-01T00:00:00Z",
    updatedAt: "2026-01-01T00:00:00Z",
    ...overrides,
  };
}

const store = () => useEditorStore.getState();

describe("editor store", () => {
  beforeEach(() => {
    resetEditorStore();
    store().load(workflow());
  });

  it("loads a workflow clean", () => {
    expect(store().nodes).toHaveLength(1);
    expect(store().dirty).toBe(false);
    expect(store().readOnly).toBe(false);
    expect(store().meta.settings).toEqual({ executionTimeoutSeconds: 3600, maxConcurrentExecutions: 10 });
  });

  it("adds nodes with generated ids, selects them and marks dirty", () => {
    const a = store().addNode("TRANSFORM", { x: 100.4, y: 20 });
    const b = store().addNode("TRANSFORM", { x: 200, y: 20 });
    expect(a).toBe("transform_1");
    expect(b).toBe("transform_2");
    expect(store().selectedNodeId).toBe("transform_2");
    expect(store().nodes.find((n) => n.id === "transform_1")?.position).toEqual({ x: 100, y: 20 });
    expect(store().dirty).toBe(true);
    expect(store().addNode("HTTP_REQUEST", { x: 0, y: 0 })).toBe("http_1");
  });

  it("enforces a single trigger and supports replacing it", () => {
    expect(store().addNode("WEBHOOK_TRIGGER", { x: 0, y: 0 })).toBeNull();
    expect(store().nodes.filter((n) => n.type === "trigger")).toHaveLength(1);
    expect(store().replaceTrigger("WEBHOOK_TRIGGER")).toBe(true);
    const trigger = store().nodes.find((n) => n.id === "trigger");
    expect(trigger?.data.nodeType).toBe("WEBHOOK_TRIGGER");
    expect(trigger?.data.config).toEqual({});
  });

  it("connects nodes and rejects self-loops, duplicates, cycles and edges into triggers", () => {
    store().addNode("LOG", { x: 0, y: 0 });
    store().addNode("LOG", { x: 0, y: 0 });
    const ok = store().connect({ source: "trigger", target: "log_1", sourceHandle: null, targetHandle: null });
    expect(ok).toEqual({ ok: true, edgeId: "e_1" });
    expect(store().connect({ source: "trigger", target: "log_1", sourceHandle: null, targetHandle: null }).ok).toBe(false);
    expect(store().connect({ source: "log_1", target: "log_1", sourceHandle: null, targetHandle: null }).ok).toBe(false);
    expect(store().connect({ source: "log_1", target: "trigger", sourceHandle: null, targetHandle: null }).ok).toBe(false);
    store().connect({ source: "log_1", target: "log_2", sourceHandle: null, targetHandle: null });
    expect(store().connect({ source: "log_2", target: "log_1", sourceHandle: null, targetHandle: null }).ok).toBe(false);
    expect(store().edges.map((e) => e.id)).toEqual(["e_1", "e_2"]);
  });

  it("requires true/false handles on condition edges", () => {
    store().addNode("CONDITION", { x: 0, y: 0 });
    store().addNode("LOG", { x: 0, y: 0 });
    store().connect({ source: "trigger", target: "condition_1", sourceHandle: null, targetHandle: null });
    expect(store().connect({ source: "condition_1", target: "log_1", sourceHandle: null, targetHandle: null }).ok).toBe(false);
    expect(store().connect({ source: "condition_1", target: "log_1", sourceHandle: "true", targetHandle: null }).ok).toBe(true);
    const def = store().toDefinition();
    expect(def.edges.find((e) => e.source === "condition_1")).toEqual({ id: "e_2", source: "condition_1", target: "log_1", sourceHandle: "true" });
    expect(def.edges.find((e) => e.source === "trigger")).toEqual({ id: "e_1", source: "trigger", target: "condition_1" });
  });

  it("deletes nodes with their edges, and edges alone", () => {
    store().addNode("LOG", { x: 0, y: 0 });
    store().connect({ source: "trigger", target: "log_1", sourceHandle: null, targetHandle: null });
    store().deleteNode("log_1");
    expect(store().nodes.map((n) => n.id)).toEqual(["trigger"]);
    expect(store().edges).toHaveLength(0);
    expect(store().selectedNodeId).toBeNull();
    store().addNode("LOG", { x: 0, y: 0 });
    store().connect({ source: "trigger", target: "log_1", sourceHandle: null, targetHandle: null });
    store().deleteEdge("e_1");
    expect(store().edges).toHaveLength(0);
  });

  it("removes connected edges when React Flow removes a node (Delete key)", () => {
    store().addNode("LOG", { x: 0, y: 0 });
    store().connect({ source: "trigger", target: "log_1", sourceHandle: null, targetHandle: null });
    store().onNodesChange([{ type: "remove", id: "log_1" }]);
    expect(store().edges).toHaveLength(0);
  });

  it("tracks dirty state: selection is not an edit, moves are, saving clears it", () => {
    store().onNodesChange([{ type: "select", id: "trigger", selected: true }]);
    expect(store().selectedNodeId).toBe("trigger");
    expect(store().dirty).toBe(false);
    store().onNodesChange([{ type: "position", id: "trigger", position: { x: 10, y: 10 } }]);
    expect(store().dirty).toBe(true);
    const counter = store().changeCounter;
    store().updateNode("trigger", { name: "Start" }); // edit while a save is in flight
    store().markSaved(2, counter);
    expect(store().revision).toBe(2);
    expect(store().dirty).toBe(true);
    store().markSaved(3, store().changeCounter);
    expect(store().dirty).toBe(false);
  });

  it("updates and clears optional node fields", () => {
    store().addNode("HTTP_REQUEST", { x: 0, y: 0 });
    store().updateNode("http_1", { timeoutSeconds: 10, retryPolicy: { maxAttempts: 2 } });
    expect(store().toDefinition().nodes[1]).toMatchObject({ timeoutSeconds: 10, retryPolicy: { maxAttempts: 2 } });
    store().updateNode("http_1", { timeoutSeconds: undefined, retryPolicy: undefined });
    expect(Object.keys(store().toDefinition().nodes[1])).not.toContain("timeoutSeconds");
    expect(Object.keys(store().toDefinition().nodes[1])).not.toContain("retryPolicy");
  });

  it("is read-only for viewers", () => {
    store().load(workflow({ role: "VIEWER" }));
    expect(store().readOnly).toBe(true);
    expect(store().addNode("LOG", { x: 0, y: 0 })).toBeNull();
    store().setMeta({ name: "x" });
    expect(store().meta.name).toBe("Test");
    expect(store().dirty).toBe(false);
  });

  it("restores a version into the draft as unsaved changes and lays out", () => {
    store().loadDefinition(loadDemoDefinition());
    expect(store().dirty).toBe(true);
    expect(store().toDefinition()).toEqual(loadDemoDefinition());
    store().applyLayout("LR");
    const xs = store().nodes.map((n) => n.position.x);
    expect(xs[0]).toBeLessThan(xs[2]);
  });

  it("generates the smallest free id", () => {
    expect(nextId("e", ["e_1", "e_3"])).toBe("e_2");
  });
});
