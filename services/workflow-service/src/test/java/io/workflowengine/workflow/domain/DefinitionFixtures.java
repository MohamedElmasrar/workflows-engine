package io.workflowengine.workflow.domain;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.workflowengine.workflow.domain.model.WorkflowDefinition;

/** Small builder for workflow definitions used in tests. */
public final class DefinitionFixtures {

    public static final ObjectMapper JSON = new ObjectMapper();

    private final ObjectNode doc = JSON.createObjectNode().put("schemaVersion", 1);
    private final ArrayNode nodes = doc.putArray("nodes");
    private final ArrayNode edges = doc.putArray("edges");
    private int edgeCounter;

    public static DefinitionFixtures definition() {
        return new DefinitionFixtures();
    }

    public DefinitionFixtures node(String id, String type, String configJson) {
        ObjectNode node = nodes.addObject().put("id", id).put("type", type).put("name", id);
        node.set("config", parse(configJson));
        return this;
    }

    public DefinitionFixtures manualTrigger(String id) {
        return node(id, "MANUAL_TRIGGER", "{}");
    }

    public DefinitionFixtures log(String id, String message) {
        return node(id, "LOG", "{\"message\": \"" + message + "\"}");
    }

    public DefinitionFixtures condition(String id) {
        return node(id, "CONDITION", "{\"conditions\": [{\"left\": \"{{ trigger.x }}\", \"operator\": \"EXISTS\"}]}");
    }

    public DefinitionFixtures edge(String source, String target) {
        return edge(source, target, null);
    }

    public DefinitionFixtures edge(String source, String target, String handle) {
        ObjectNode edge =
                edges.addObject().put("id", "e" + (++edgeCounter)).put("source", source).put("target", target);
        if (handle != null) {
            edge.put("sourceHandle", handle);
        }
        return this;
    }

    public JsonNode json() {
        return doc.deepCopy();
    }

    public WorkflowDefinition build() {
        return WorkflowDefinition.of(doc);
    }

    public static JsonNode parse(String json) {
        try {
            return JSON.readTree(json);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
