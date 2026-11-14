package io.workflowengine.execution.domain;

import static io.workflowengine.execution.domain.Graphs.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.execution.domain.service.TemplateResolver;
import java.util.Map;
import org.junit.jupiter.api.Test;

class TemplateResolverTest {

    private final TemplateResolver.Context context = new TemplateResolver.Context(
            json("{\"body\":{\"name\":\"Ada\",\"tags\":[\"a\",\"b\"],\"n\":3}}"),
            Map.of("http", json("{\"status\":200,\"body\":{\"items\":[{\"id\":7}]}}")),
            json("{\"id\":\"exec-1\"}"));

    private JsonNode resolve(String template) {
        return TemplateResolver.resolve(json(template), context);
    }

    @Test
    void wholeValuePlaceholderKeepsJsonType() {
        assertThat(resolve("{\"v\":\"{{ trigger.body.n }}\"}").get("v").isInt()).isTrue();
        assertThat(resolve("{\"v\":\"{{trigger.body.tags}}\"}").get("v").isArray()).isTrue();
        assertThat(resolve("{\"v\":\"{{ nodes.http.output.body.items[0].id }}\"}").get("v").asInt()).isEqualTo(7);
    }

    @Test
    void interpolationProducesText() {
        assertThat(resolve("{\"v\":\"Hi {{ trigger.body.name }} ({{ execution.id }})\"}").get("v").asText())
                .isEqualTo("Hi Ada (exec-1)");
    }

    @Test
    void missingPathsResolveToNullOrEmptyText() {
        assertThat(resolve("{\"v\":\"{{ trigger.body.missing }}\"}").get("v").isNull()).isTrue();
        assertThat(resolve("{\"v\":\"x{{ nodes.unknown.output.a }}y\"}").get("v").asText()).isEqualTo("xy");
    }

    @Test
    void unsupportedExpressionsAreNotEvaluated() {
        assertThat(resolve("{\"v\":\"{{ T(java.lang.Runtime).getRuntime() }}\"}").get("v").isNull()).isTrue();
        assertThat(resolve("{\"v\":\"{{ trigger.body.name + 1 }}\"}").get("v").isNull()).isTrue();
    }

    @Test
    void nestedStructuresAreResolvedRecursively() {
        JsonNode result = resolve("{\"a\":[{\"b\":\"{{ trigger.body.name }}\"}],\"c\":5}");
        assertThat(result.at("/a/0/b").asText()).isEqualTo("Ada");
        assertThat(result.get("c").asInt()).isEqualTo(5);
    }
}
