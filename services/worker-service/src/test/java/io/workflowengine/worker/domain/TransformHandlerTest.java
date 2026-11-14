package io.workflowengine.worker.domain;

import static io.workflowengine.worker.domain.ConditionHandlerTest.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.worker.domain.handler.TransformHandler;
import io.workflowengine.worker.domain.model.NodeResult;
import org.junit.jupiter.api.Test;

class TransformHandlerTest {

    private final TransformHandler handler = new TransformHandler();

    private JsonNode run(String config) {
        return ((NodeResult.Success) handler.execute(Contexts.context(), json(config))).output();
    }

    @Test
    void picksRenamesAndSetsFields() {
        JsonNode out = run("""
                {"source": {"id": 1, "name": "Ada", "password": "x"}, "pick": ["id", "name"],
                 "rename": {"name": "fullName"}, "set": {"source": "crm", "id": 2}}""");
        assertThat(out.toString()).isEqualTo("{\"id\":2,\"fullName\":\"Ada\",\"source\":\"crm\"}");
    }

    @Test
    void constructsObjectsFromSetOnly() {
        assertThat(run("{\"set\": {\"a\": [1,2], \"b\": {\"c\": true}}}").at("/b/c").asBoolean()).isTrue();
    }

    @Test
    void wrapsNonObjectSource() {
        assertThat(run("{\"source\": [1,2,3]}").get("value").size()).isEqualTo(3);
    }
}
