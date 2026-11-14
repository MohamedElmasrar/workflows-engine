package io.workflowengine.workflow.infrastructure.schema;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion;
import io.workflowengine.workflow.application.port.out.DefinitionSchemaValidator;
import io.workflowengine.workflow.domain.service.ValidationIssue;
import io.workflowengine.workflow.domain.service.ValidationResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/** Validates definitions against {@code workflow-definition.v1.json} shipped in the contracts module. */
@Component
public class JsonSchemaDefinitionValidator implements DefinitionSchemaValidator {

    private static final String SCHEMA = "/schemas/workflow-definition.v1.json";
    private static final Pattern NODE_PATH = Pattern.compile("^/nodes/(\\d+)");
    private static final Pattern EDGE_PATH = Pattern.compile("^/edges/(\\d+)");

    private final JsonSchema schema;

    public JsonSchemaDefinitionValidator() {
        try (InputStream in = getClass().getResourceAsStream(SCHEMA)) {
            if (in == null) {
                throw new IllegalStateException("Missing " + SCHEMA + " on the classpath");
            }
            schema = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012)
                    .getSchema(in, SchemaValidatorsConfig.builder().build());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public ValidationResult validate(JsonNode document) {
        List<ValidationIssue> issues = schema.validate(document).stream()
                .sorted(Comparator.comparing(m -> m.getInstanceLocation().toString()))
                .map(m -> {
                    String location = m.getInstanceLocation().toString();
                    return new ValidationIssue(
                            "SCHEMA_VIOLATION",
                            location + ": " + m.getMessage().replaceFirst("^\\$[^:]*: ", ""),
                            elementId(document, NODE_PATH, "nodes", location),
                            elementId(document, EDGE_PATH, "edges", location));
                })
                .distinct()
                .toList();
        return new ValidationResult(issues);
    }

    private static String elementId(JsonNode document, Pattern pattern, String field, String location) {
        Matcher m = pattern.matcher(location);
        if (!m.find()) {
            return null;
        }
        JsonNode id = document.path(field).path(Integer.parseInt(m.group(1))).get("id");
        return id != null && id.isTextual() ? id.asText() : null;
    }
}
