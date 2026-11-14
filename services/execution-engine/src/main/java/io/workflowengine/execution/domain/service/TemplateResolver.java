package io.workflowengine.execution.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.MissingNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.Iterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code {{ path }}} placeholders against execution data. Only property paths are supported; there is no
 * evaluation of operators, functions or code. See docs/architecture/overview.md, "Templates".
 */
public final class TemplateResolver {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]*?)\\s*}}");
    private static final Pattern WHOLE = Pattern.compile("^\\s*\\{\\{\\s*([^{}]*?)\\s*}}\\s*$");
    private static final Pattern SEGMENT = Pattern.compile("\\.([A-Za-z0-9_-]+)|\\[(\\d+)]");
    private static final JsonNodeFactory F = JsonNodeFactory.instance;

    /** Data visible to templates: the trigger output, outputs of completed nodes and execution metadata. */
    public record Context(JsonNode trigger, Map<String, JsonNode> nodeOutputs, JsonNode execution) {}

    private TemplateResolver() {}

    public static JsonNode resolve(JsonNode value, Context context) {
        if (value == null) {
            return NullNode.getInstance();
        }
        if (value.isTextual()) {
            return resolveText(value.asText(), context);
        }
        if (value.isObject()) {
            ObjectNode copy = F.objectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = value.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                copy.set(field.getKey(), resolve(field.getValue(), context));
            }
            return copy;
        }
        if (value.isArray()) {
            ArrayNode copy = F.arrayNode();
            value.forEach(element -> copy.add(resolve(element, context)));
            return copy;
        }
        return value;
    }

    private static JsonNode resolveText(String text, Context context) {
        Matcher whole = WHOLE.matcher(text);
        if (whole.matches()) {
            JsonNode resolved = lookup(whole.group(1), context);
            return resolved.isMissingNode() ? NullNode.getInstance() : resolved;
        }
        Matcher m = PLACEHOLDER.matcher(text);
        if (!m.find()) {
            return F.textNode(text);
        }
        StringBuilder out = new StringBuilder();
        m.reset();
        while (m.find()) {
            JsonNode resolved = lookup(m.group(1), context);
            String replacement = resolved.isMissingNode() || resolved.isNull()
                    ? ""
                    : resolved.isValueNode() ? resolved.asText() : resolved.toString();
            m.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        m.appendTail(out);
        return F.textNode(out.toString());
    }

    /** Evaluates a path such as {@code nodes.http.output.body.items[0].id}. Unknown roots resolve to missing. */
    static JsonNode lookup(String path, Context context) {
        String rest;
        JsonNode current;
        if (path.equals("trigger") || path.startsWith("trigger.") || path.startsWith("trigger[")) {
            current = context.trigger();
            rest = path.substring("trigger".length());
        } else if (path.startsWith("execution.")) {
            current = context.execution();
            rest = path.substring("execution".length());
        } else if (path.startsWith("nodes.")) {
            Matcher node = Pattern.compile("^nodes\\.([A-Za-z][A-Za-z0-9_-]*)\\.output")
                    .matcher(path);
            if (!node.find()) {
                return MissingNode.getInstance();
            }
            current = context.nodeOutputs().get(node.group(1));
            rest = path.substring(node.end());
        } else {
            return MissingNode.getInstance();
        }
        if (current == null) {
            return MissingNode.getInstance();
        }
        Matcher segment = SEGMENT.matcher(rest);
        int position = 0;
        while (segment.find()) {
            if (segment.start() != position) {
                return MissingNode.getInstance();
            }
            position = segment.end();
            current = segment.group(1) != null
                    ? current.path(segment.group(1))
                    : current.path(Integer.parseInt(segment.group(2)));
            if (current.isMissingNode()) {
                return current;
            }
        }
        return position == rest.length() ? current : MissingNode.getInstance();
    }
}
