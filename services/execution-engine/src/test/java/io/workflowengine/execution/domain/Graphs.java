package io.workflowengine.execution.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.workflowengine.execution.domain.model.ExecutionGraph;

/** Fluent builder for workflow definitions in tests. */
public final class Graphs {

    public static final ObjectMapper JSON = new ObjectMapper();
    private final ObjectNode doc = JSON.createObjectNode().put("schemaVersion", 1);
    private final ArrayNode nodes = doc.putArray("nodes");
    private final ArrayNode edges = doc.putArray("edges");
    private int edge;

    public static Graphs graph() {
        return new Graphs().node("trigger", "MANUAL_TRIGGER", "{}");
    }

    public Graphs node(String id, String type, String config) {
        ObjectNode n = nodes.addObject().put("id", id).put("type", type).put("name", id);
        n.set("config", json(config));
        return this;
    }

    public Graphs log(String id) {
        return node(id, "LOG", "{\"message\":\"" + id + "\"}");
    }

    public Graphs retry(String id, int maxAttempts, long initialMs) {
        for (JsonNode n : nodes) {
            if (n.get("id").asText().equals(id)) {
                ((ObjectNode) n).putObject("retryPolicy").put("maxAttempts", maxAttempts).put("initialIntervalMs", initialMs)
                        .put("multiplier", 2).put("maxIntervalMs", 60000);
            }
        }
        return this;
    }

    public Graphs timeout(String id, int seconds) {
        for (JsonNode n : nodes) {
            if (n.get("id").asText().equals(id)) {
                ((ObjectNode) n).put("timeoutSeconds", seconds);
            }
        }
        return this;
    }

    public Graphs executionTimeout(int seconds) {
        doc.putObject("settings").put("executionTimeoutSeconds", seconds);
        return this;
    }

    public Graphs edge(String from, String to) {
        return edge(from, to, null);
    }

    public Graphs edge(String from, String to, String handle) {
        ObjectNode e = edges.addObject().put("id", "e" + (++edge)).put("source", from).put("target", to);
        if (handle != null) {
            e.put("sourceHandle", handle);
        }
        return this;
    }

    public JsonNode json() {
        return doc.deepCopy();
    }

    public ExecutionGraph build() {
        return ExecutionGraph.parse(doc);
    }

    public static JsonNode json(String text) {
        try {
            return JSON.readTree(text);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
