package io.workflowengine.workflow.domain.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Parses {@code {{ path }}} placeholders in node configuration. The language is deliberately limited to property
 * paths so user input can never be evaluated as code.
 */
public final class TemplateReferences {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([^{}]*?)\\s*}}");
    private static final String SEGMENT = "(?:\\.[A-Za-z0-9_-]+|\\[\\d+])*";
    private static final Pattern TRIGGER = Pattern.compile("^trigger" + SEGMENT + "$");
    private static final Pattern NODE = Pattern.compile("^nodes\\.([A-Za-z][A-Za-z0-9_-]*)\\.output" + SEGMENT + "$");
    private static final Pattern EXECUTION = Pattern.compile("^execution\\.(id|workflowId|workflowVersion)$");

    /** A parsed placeholder. {@code nodeId} is set for {@code nodes.<id>.output...} references. */
    public record Reference(String expression, boolean valid, String nodeId) {}

    private TemplateReferences() {}

    public static List<Reference> find(JsonNode config) {
        List<Reference> references = new ArrayList<>();
        collect(config, references);
        return references;
    }

    private static void collect(JsonNode node, List<Reference> out) {
        if (node == null) {
            return;
        }
        if (node.isTextual()) {
            Matcher m = PLACEHOLDER.matcher(node.asText());
            while (m.find()) {
                out.add(parse(m.group(1)));
            }
        } else if (node.isContainerNode()) {
            node.fields().forEachRemaining(e -> {
                collect(e.getValue(), out);
            });
            if (node.isArray()) {
                node.forEach(child -> collect(child, out));
            }
        }
    }

    static Reference parse(String expression) {
        Matcher node = NODE.matcher(expression);
        if (node.matches()) {
            return new Reference(expression, true, node.group(1));
        }
        boolean valid = TRIGGER.matcher(expression).matches()
                || EXECUTION.matcher(expression).matches();
        return new Reference(expression, valid, null);
    }
}
