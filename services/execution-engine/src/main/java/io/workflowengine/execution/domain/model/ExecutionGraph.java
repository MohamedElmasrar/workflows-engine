package io.workflowengine.execution.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.workflowengine.contracts.NodeType;
import io.workflowengine.execution.domain.exception.InvalidWorkflowVersionException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only DAG built from a published workflow version, with adjacency lists so that scheduling only inspects the
 * direct neighbours of a node that changed state. The workflow service validated the graph at publication; this
 * class re-checks the invariants the engine relies on.
 */
public final class ExecutionGraph {

    public static final Duration DEFAULT_NODE_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration DEFAULT_EXECUTION_TIMEOUT = Duration.ofHours(1);
    public static final int DEFAULT_MAX_CONCURRENT_EXECUTIONS = 10;

    private final Map<String, GraphNode> nodes;
    private final Map<String, List<GraphEdge>> outgoing = new HashMap<>();
    private final Map<String, List<GraphEdge>> incoming = new HashMap<>();
    private final GraphNode trigger;
    private final Duration executionTimeout;
    private final int maxConcurrentExecutions;

    private ExecutionGraph(
            Map<String, GraphNode> nodes, List<GraphEdge> edges, Duration executionTimeout, int maxConcurrent) {
        this.nodes = nodes;
        this.executionTimeout = executionTimeout;
        this.maxConcurrentExecutions = maxConcurrent;
        for (GraphEdge edge : edges) {
            if (!nodes.containsKey(edge.source()) || !nodes.containsKey(edge.target())) {
                throw new InvalidWorkflowVersionException("Edge " + edge.id() + " references an unknown node");
            }
            outgoing.computeIfAbsent(edge.source(), k -> new ArrayList<>()).add(edge);
            incoming.computeIfAbsent(edge.target(), k -> new ArrayList<>()).add(edge);
        }
        List<GraphNode> triggers =
                nodes.values().stream().filter(n -> n.type().isTrigger()).toList();
        if (triggers.size() != 1) {
            throw new InvalidWorkflowVersionException("A workflow version must contain exactly one trigger");
        }
        this.trigger = triggers.get(0);
    }

    public static ExecutionGraph parse(JsonNode definition) {
        Map<String, GraphNode> nodes = new LinkedHashMap<>();
        for (JsonNode n : definition.path("nodes")) {
            NodeType type;
            try {
                type = NodeType.valueOf(n.path("type").asText());
            } catch (IllegalArgumentException e) {
                throw new InvalidWorkflowVersionException("Unknown node type " + n.path("type").asText());
            }
            Duration timeout = n.has("timeoutSeconds")
                    ? Duration.ofSeconds(n.get("timeoutSeconds").asLong())
                    : DEFAULT_NODE_TIMEOUT;
            JsonNode config = n.has("config") ? n.get("config") : JsonNodeFactory.instance.objectNode();
            String id = n.path("id").asText();
            nodes.put(id, new GraphNode(id, type, n.path("name").asText(id), config, timeout, retryPolicy(n.get("retryPolicy"))));
        }
        List<GraphEdge> edges = new ArrayList<>();
        for (JsonNode e : definition.path("edges")) {
            JsonNode handle = e.get("sourceHandle");
            edges.add(new GraphEdge(
                    e.path("id").asText(),
                    e.path("source").asText(),
                    e.path("target").asText(),
                    handle == null || handle.isNull() ? null : handle.asText()));
        }
        JsonNode settings = definition.path("settings");
        Duration executionTimeout = settings.has("executionTimeoutSeconds")
                ? Duration.ofSeconds(settings.get("executionTimeoutSeconds").asLong())
                : DEFAULT_EXECUTION_TIMEOUT;
        int maxConcurrent = settings.path("maxConcurrentExecutions").asInt(DEFAULT_MAX_CONCURRENT_EXECUTIONS);
        return new ExecutionGraph(nodes, edges, executionTimeout, maxConcurrent);
    }

    private static RetryPolicy retryPolicy(JsonNode json) {
        if (json == null || json.isNull()) {
            return ExponentialBackoffRetryPolicy.DEFAULT;
        }
        return new ExponentialBackoffRetryPolicy(
                json.path("maxAttempts").asInt(1),
                Duration.ofMillis(json.path("initialIntervalMs").asLong(1000)),
                json.path("multiplier").asDouble(2.0),
                Duration.ofMillis(json.path("maxIntervalMs").asLong(60_000)));
    }

    public GraphNode node(String id) {
        GraphNode node = nodes.get(id);
        if (node == null) {
            throw new IllegalArgumentException("Unknown node " + id);
        }
        return node;
    }

    public boolean contains(String id) {
        return nodes.containsKey(id);
    }

    public Collection<GraphNode> nodes() {
        return nodes.values();
    }

    public List<GraphEdge> outgoing(String nodeId) {
        return outgoing.getOrDefault(nodeId, List.of());
    }

    public List<GraphEdge> incoming(String nodeId) {
        return incoming.getOrDefault(nodeId, List.of());
    }

    public boolean isSink(String nodeId) {
        return outgoing(nodeId).isEmpty();
    }

    public GraphNode trigger() {
        return trigger;
    }

    public Duration executionTimeout() {
        return executionTimeout;
    }

    public int maxConcurrentExecutions() {
        return maxConcurrentExecutions;
    }
}
