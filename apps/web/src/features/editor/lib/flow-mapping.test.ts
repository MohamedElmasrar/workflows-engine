import { describe, expect, it } from "vitest";
import { loadDemoDefinition } from "@/test/fixtures";
import type { WorkflowDefinition } from "@/lib/api/types";
import { ancestorsOf, createsCycle, definitionToFlow, flowToDefinition } from "./flow-mapping";

describe("definitionToFlow / flowToDefinition", () => {
  it("round-trips the order webhook demo exactly", () => {
    const demo = loadDemoDefinition();
    const graph = definitionToFlow(demo);
    const back = flowToDefinition(graph.nodes, graph.edges, graph.settings);
    expect(back).toEqual(demo);
    // Key-for-key identical, including no extra fields anywhere.
    expect(JSON.parse(JSON.stringify(back))).toStrictEqual(demo);
  });

  it("maps node categories, data and branch handles", () => {
    const graph = definitionToFlow(loadDemoDefinition());
    const byId = new Map(graph.nodes.map((n) => [n.id, n]));
    expect(byId.get("webhook")?.type).toBe("trigger");
    expect(byId.get("risky")?.type).toBe("condition");
    expect(byId.get("score")?.type).toBe("action");
    expect(byId.get("score")?.data.retryPolicy).toEqual({ maxAttempts: 3, initialIntervalMs: 1000, multiplier: 2, maxIntervalMs: 10000 });
    expect(byId.get("score")?.data.timeoutSeconds).toBe(10);
    const e4 = graph.edges.find((e) => e.id === "e4");
    expect(e4?.sourceHandle).toBe("true");
    expect(e4?.label).toBe("true");
    expect(graph.edges.find((e) => e.id === "e1")?.sourceHandle).toBeNull();
  });

  it("does not share config objects with the input (pure)", () => {
    const demo = loadDemoDefinition();
    const graph = definitionToFlow(demo);
    (graph.nodes[1].data.config.set as Record<string, unknown>).orderId = "changed";
    expect((demo.nodes[1].config.set as Record<string, unknown>).orderId).toBe("{{ trigger.body.orderId }}");
  });

  it("lays out nodes that have no position and never emits extra fields", () => {
    const def: WorkflowDefinition = {
      schemaVersion: 1,
      nodes: [
        { id: "t", type: "MANUAL_TRIGGER", name: "Start", config: {} },
        { id: "l", type: "LOG", name: "Log", config: { message: "hi" } },
      ],
      edges: [{ id: "e_1", source: "t", target: "l", sourceHandle: null }],
    };
    const graph = definitionToFlow(def);
    expect(graph.nodes[1].position.x).toBeGreaterThan(graph.nodes[0].position.x);
    const back = flowToDefinition(graph.nodes, graph.edges, graph.settings);
    expect(back.settings).toBeUndefined();
    expect(Object.keys(back.edges[0]).sort()).toEqual(["id", "source", "target"]);
    expect(Object.keys(back.nodes[0]).sort()).toEqual(["config", "id", "name", "position", "type"]);
  });

  it("drops React Flow runtime state when converting back", () => {
    const graph = definitionToFlow(loadDemoDefinition());
    const nodes = graph.nodes.map((n) => ({ ...n, selected: true, dragging: false, measured: { width: 240, height: 76 } }));
    const edges = graph.edges.map((e) => ({ ...e, selected: true, animated: true, className: "x" }));
    expect(flowToDefinition(nodes, edges, graph.settings)).toEqual(loadDemoDefinition());
  });
});

describe("graph helpers", () => {
  const edges = [
    { source: "a", target: "b" },
    { source: "b", target: "c" },
    { source: "a", target: "d" },
  ];
  it("finds ancestors", () => {
    expect(ancestorsOf("c", edges)).toEqual(["b", "a"]);
    expect(ancestorsOf("a", edges)).toEqual([]);
  });
  it("detects cycles", () => {
    expect(createsCycle("c", "a", edges)).toBe(true);
    expect(createsCycle("d", "c", edges)).toBe(false);
    expect(createsCycle("a", "a", edges)).toBe(true);
  });
});
