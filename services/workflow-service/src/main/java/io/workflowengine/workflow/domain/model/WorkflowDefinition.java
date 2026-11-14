package io.workflowengine.workflow.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.workflowengine.contracts.NodeType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * A workflow graph as authored. Holds the original JSON document (the persisted and published form) and a typed view
 * of the parts the domain reasons about. Unknown or malformed parts are tolerated here and reported by validation.
 */
public final class WorkflowDefinition {

    public static final int SCHEMA_VERSION = 1;

    private final JsonNode document;
    private final List<Node> nodes;
    private final List<Edge> edges;

    public record Node(String id, String type, String name, JsonNode config) {
        public Optional<NodeType> nodeType() {
            try {
                return Optional.of(NodeType.valueOf(type));
            } catch (IllegalArgumentException | NullPointerException e) {
                return Optional.empty();
            }
        }

        public boolean isTrigger() {
            return nodeType().map(NodeType::isTrigger).orElse(false);
        }
    }

    public record Edge(String id, String source, String target, String sourceHandle) {}

    private WorkflowDefinition(JsonNode document) {
        this.document = Objects.requireNonNull(document, "document").deepCopy();
        this.nodes = parseNodes(this.document);
        this.edges = parseEdges(this.document);
    }

    public static WorkflowDefinition of(JsonNode document) {
        return new WorkflowDefinition(document);
    }

    /** A new workflow starts with a single manual trigger so it is immediately runnable once published. */
    public static WorkflowDefinition initial() {
        JsonNodeFactory f = JsonNodeFactory.instance;
        ObjectNode doc = f.objectNode().put("schemaVersion", SCHEMA_VERSION);
        ObjectNode trigger = doc.putArray("nodes").addObject();
        trigger.put("id", "trigger").put("type", NodeType.MANUAL_TRIGGER.name()).put("name", "Manual trigger");
        trigger.putObject("position").put("x", 0).put("y", 0);
        trigger.putObject("config");
        doc.putArray("edges");
        doc.putObject("settings").put("executionTimeoutSeconds", 3600).put("maxConcurrentExecutions", 10);
        return new WorkflowDefinition(doc);
    }

    public JsonNode document() {
        return document.deepCopy();
    }

    public List<Node> nodes() {
        return nodes;
    }

    public List<Edge> edges() {
        return edges;
    }

    public List<Node> triggerNodes() {
        return nodes.stream().filter(Node::isTrigger).toList();
    }

    public Optional<Node> trigger() {
        List<Node> triggers = triggerNodes();
        return triggers.size() == 1 ? Optional.of(triggers.get(0)) : Optional.empty();
    }

    private static List<Node> parseNodes(JsonNode doc) {
        List<Node> result = new ArrayList<>();
        for (JsonNode n : doc.path("nodes")) {
            result.add(new Node(text(n, "id"), text(n, "type"), text(n, "name"), n.path("config")));
        }
        return List.copyOf(result);
    }

    private static List<Edge> parseEdges(JsonNode doc) {
        List<Edge> result = new ArrayList<>();
        for (JsonNode e : doc.path("edges")) {
            result.add(new Edge(text(e, "id"), text(e, "source"), text(e, "target"), text(e, "sourceHandle")));
        }
        return List.copyOf(result);
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof WorkflowDefinition other && document.equals(other.document);
    }

    @Override
    public int hashCode() {
        return document.hashCode();
    }
}
