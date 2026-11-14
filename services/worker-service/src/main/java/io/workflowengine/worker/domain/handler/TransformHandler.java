package io.workflowengine.worker.domain.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.workflowengine.contracts.NodeType;
import io.workflowengine.worker.domain.model.NodeExecutionContext;
import io.workflowengine.worker.domain.model.NodeExecutionException;
import io.workflowengine.worker.domain.model.NodeResult;
import java.util.Iterator;
import java.util.Map;

/**
 * Restricted JSON mapping: start from {@code source} (an object from upstream data), keep only {@code pick} fields,
 * apply {@code rename}, then merge the constructed {@code set} object. Values were already mapped from upstream
 * outputs by the engine's template resolution.
 */
public class TransformHandler implements NodeHandler {

    @Override
    public NodeType supportedType() {
        return NodeType.TRANSFORM;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context, JsonNode config) {
        ObjectNode output = JsonNodeFactory.instance.objectNode();
        JsonNode source = config.get("source");
        if (source != null && !source.isNull()) {
            if (source.isObject()) {
                copySource((ObjectNode) source, config.get("pick"), output);
            } else {
                output.set("value", source);
            }
        }
        JsonNode rename = config.get("rename");
        if (rename != null && rename.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = rename.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> entry = fields.next();
                if (!entry.getValue().isTextual() || entry.getValue().asText().isBlank()) {
                    throw NodeExecutionException.invalidConfig("rename targets must be non-empty strings");
                }
                JsonNode value = output.remove(entry.getKey());
                if (value != null) {
                    output.set(entry.getValue().asText(), value);
                }
            }
        }
        JsonNode set = config.get("set");
        if (set != null && !set.isNull()) {
            if (!set.isObject()) {
                throw NodeExecutionException.invalidConfig("'set' must be an object");
            }
            output.setAll((ObjectNode) set);
        }
        return NodeResult.success(output);
    }

    private static void copySource(ObjectNode source, JsonNode pick, ObjectNode output) {
        if (pick == null || pick.isNull()) {
            output.setAll(source);
            return;
        }
        if (!pick.isArray()) {
            throw NodeExecutionException.invalidConfig("'pick' must be a list of field names");
        }
        for (JsonNode field : pick) {
            JsonNode value = source.get(field.asText());
            if (value != null) {
                output.set(field.asText(), value);
            }
        }
    }
}
