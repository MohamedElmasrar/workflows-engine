import { describe, expect, it } from "vitest";
import { loadDemoDefinition } from "@/test/fixtures";
import type { NodeType } from "@/lib/api/types";
import { NODE_TYPES } from "@/lib/api/types";
import { defaultConfig, isFiveFieldCron, validateNode, validateNodeConfig } from "./node-configs";
import { conditionForm, formatLiteral, httpForm, parseLiteral, transformForm, webhookForm } from "./node-forms";

const valid: [NodeType, unknown][] = [
  ["MANUAL_TRIGGER", {}],
  ["WEBHOOK_TRIGGER", { requireSignature: true, requiredFields: ["orderId"] }],
  ["SCHEDULE_TRIGGER", { cron: "0 9 * * 1-5", timezone: "Europe/Berlin", payload: { a: 1 } }],
  ["HTTP_REQUEST", { method: "POST", url: "http://x", headers: { A: "b" }, query: { n: 1, f: true, s: "x" }, body: [1, 2], failOnHttpError: false }],
  ["TRANSFORM", { source: "{{ trigger.body }}", pick: ["a"], rename: { a: "b" }, set: { c: 1 } }],
  ["LOG", { message: "hi", level: "WARN" }],
  ["NOTIFICATION", { channel: "EMAIL", recipient: "a@b.c", subject: "s", message: "m" }],
  ["CONDITION", { combinator: "OR", conditions: [{ left: 1, operator: "EXISTS" }, { left: "{{ x }}", operator: "EQUALS", right: null }] }],
  ["DELAY", { durationSeconds: 604800 }],
];

const invalid: [NodeType, unknown, string][] = [
  ["MANUAL_TRIGGER", { foo: 1 }, "additional property"],
  ["WEBHOOK_TRIGGER", { requiredFields: [""] }, "empty field name"],
  ["SCHEDULE_TRIGGER", { cron: "* * * * * *", timezone: "UTC" }, "six cron fields"],
  ["SCHEDULE_TRIGGER", { cron: "0 9 * * *" }, "missing timezone"],
  ["HTTP_REQUEST", { method: "HEAD", url: "http://x" }, "unsupported method"],
  ["HTTP_REQUEST", { method: "GET", url: "" }, "empty url"],
  ["HTTP_REQUEST", { method: "GET", url: "http://x", headers: { a: 1 } }, "non-string header"],
  ["HTTP_REQUEST", { method: "GET", url: "http://x", query: { a: null } }, "null query value"],
  ["TRANSFORM", { set: [1] }, "set must be object"],
  ["LOG", { message: "" }, "empty message"],
  ["LOG", { message: "x", level: "DEBUG" }, "unknown level"],
  ["NOTIFICATION", { channel: "SMS", recipient: "a", message: "m" }, "unknown channel"],
  ["CONDITION", { conditions: [] }, "no conditions"],
  ["CONDITION", { conditions: [{ operator: "EQUALS", right: 1 }] }, "missing left"],
  ["CONDITION", { conditions: [{ left: 1, operator: "MATCHES" }] }, "unknown operator"],
  ["DELAY", { durationSeconds: 0 }, "zero duration"],
  ["DELAY", { durationSeconds: 1.5 }, "fractional duration"],
  ["DELAY", { durationSeconds: 604801 }, "too long"],
];

describe("node config schemas", () => {
  it.each(valid)("accepts a valid %s config", (type, config) => {
    expect(validateNodeConfig(type, config)).toEqual({ success: true });
  });

  it.each(invalid)("rejects %s config (%s → %s)", (type, config) => {
    expect(validateNodeConfig(type, config).success).toBe(false);
  });

  it("accepts every node of the demo definition", () => {
    for (const node of loadDemoDefinition().nodes) {
      expect(validateNode(node), node.id).toEqual([]);
    }
  });

  it("produces schema-valid defaults for every node type", () => {
    for (const type of NODE_TYPES) expect(validateNodeConfig(type, defaultConfig(type)).success, type).toBe(true);
  });

  it("validates node-level fields", () => {
    expect(validateNode({ type: "LOG", name: "", config: { message: "x" } })[0]?.path).toBe("name");
    expect(validateNode({ type: "LOG", name: "a", config: { message: "x" }, timeoutSeconds: 301 })[0]?.path).toBe("timeoutSeconds");
    expect(validateNode({ type: "LOG", name: "a", config: { message: "x" }, retryPolicy: { maxAttempts: 11 } })[0]?.path).toBe("retryPolicy");
    expect(validateNode({ type: "LOG", name: "a", config: { message: "x" }, retryPolicy: { maxAttempts: 2, initialIntervalMs: 500 } })).toEqual([]);
  });

  it("checks cron field counts", () => {
    expect(isFiveFieldCron("*/5 * * * *")).toBe(true);
    expect(isFiveFieldCron("0 9 * *")).toBe(false);
  });
});

describe("form converters", () => {
  it("parses JSON literals and keeps templates as strings", () => {
    expect(parseLiteral("70")).toBe(70);
    expect(parseLiteral('"70"')).toBe("70");
    expect(parseLiteral("{{ trigger.x }}")).toBe("{{ trigger.x }}");
    expect(parseLiteral("")).toBeUndefined();
    expect(formatLiteral("70")).toBe('"70"');
    expect(formatLiteral("{{ a }}")).toBe("{{ a }}");
    expect(formatLiteral(true)).toBe("true");
  });

  it("round-trips the demo condition and HTTP configs through their forms", () => {
    const demo = loadDemoDefinition();
    const condition = demo.nodes.find((n) => n.id === "risky")!;
    expect(conditionForm.fromForm(conditionForm.toForm(condition.config), condition.config)).toEqual(condition.config);
    const http = demo.nodes.find((n) => n.id === "score")!;
    expect(httpForm.fromForm(httpForm.toForm(http.config), http.config)).toEqual(http.config);
    const webhook = demo.nodes.find((n) => n.id === "webhook")!;
    expect(webhookForm.fromForm(webhookForm.toForm(webhook.config), webhook.config)).toEqual(webhook.config);
  });

  it("omits empty optional fields", () => {
    expect(transformForm.fromForm({ source: "", pick: [{ value: " " }], rename: [], set: "" })).toEqual({});
    const http = httpForm.fromForm(
      { method: "GET", url: " https://x ", headers: [], query: [{ key: "n", value: "5", kind: "number" }], body: "", failOnHttpError: true },
      {},
    );
    expect(http).toEqual({ method: "GET", url: "https://x", query: { n: 5 } });
  });

  it("drops the right operand for EXISTS", () => {
    expect(
      conditionForm.fromForm({ combinator: "AND", conditions: [{ left: "{{ a }}", operator: "EXISTS", right: "1" }] }, {}),
    ).toEqual({ conditions: [{ left: "{{ a }}", operator: "EXISTS" }] });
  });
});
