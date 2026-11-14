package io.workflowengine.execution.domain.policy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Replaces values of sensitive-looking keys (authorization, password, token, secret, api key, cookie) with a mask.
 * Applied to stored node inputs and to every input/output returned by the API.
 */
public final class SensitiveDataMasker {

    public static final String MASK = "••••••";
    private static final Pattern SENSITIVE = Pattern.compile(
            "(?i).*(authorization|password|passwd|secret|token|api[-_]?key|cookie|credential|private[-_]?key).*");

    private SensitiveDataMasker() {}

    public static JsonNode mask(JsonNode value) {
        if (value == null) {
            return null;
        }
        if (value.isObject()) {
            ObjectNode copy = JsonNodeFactory.instance.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (SENSITIVE.matcher(field.getKey()).matches() && field.getValue().isValueNode()
                        && !field.getValue().isNull()) {
                    copy.put(field.getKey(), MASK);
                } else {
                    copy.set(field.getKey(), mask(field.getValue()));
                }
            }
            return copy;
        }
        if (value.isArray()) {
            ArrayNode copy = JsonNodeFactory.instance.arrayNode();
            value.forEach(v -> copy.add(mask(v)));
            return copy;
        }
        return value;
    }
}
