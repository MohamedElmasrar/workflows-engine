package io.workflowengine.trigger.domain.model;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.workflowengine.contracts.NodeType;
import java.util.ArrayList;
import java.util.List;

/** Trigger configuration extracted from a published workflow version (its single trigger node). */
public record TriggerSpec(
        TriggerKind kind,
        boolean requireSignature,
        List<String> requiredFields,
        String cron,
        String timezone,
        JsonNode schedulePayload) {

    public static TriggerSpec fromDefinition(JsonNode definition) {
        for (JsonNode node : definition.path("nodes")) {
            NodeType type;
            try {
                type = NodeType.valueOf(node.path("type").asText());
            } catch (IllegalArgumentException e) {
                continue;
            }
            if (!type.isTrigger()) {
                continue;
            }
            JsonNode config = node.path("config");
            List<String> required = new ArrayList<>();
            config.path("requiredFields").forEach(f -> required.add(f.asText()));
            JsonNode payload = config.has("payload") ? config.get("payload") : JsonNodeFactory.instance.objectNode();
            return new TriggerSpec(
                    TriggerKind.of(type),
                    config.path("requireSignature").asBoolean(false),
                    List.copyOf(required),
                    config.path("cron").asText(null),
                    config.path("timezone").asText("UTC"),
                    payload);
        }
        throw new IllegalArgumentException("Workflow version has no trigger node");
    }
}
