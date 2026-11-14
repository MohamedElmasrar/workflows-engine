package io.workflowengine.worker.domain.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.workflowengine.contracts.NodeType;
import io.workflowengine.worker.domain.model.NodeExecutionContext;
import io.workflowengine.worker.domain.model.NodeExecutionException;
import io.workflowengine.worker.domain.model.NodeResult;

/** Evaluates a list of typed comparisons combined with AND/OR and selects the {@code true} or {@code false} branch. */
public class ConditionHandler implements NodeHandler {

    @Override
    public NodeType supportedType() {
        return NodeType.CONDITION;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context, JsonNode config) {
        JsonNode conditions = config.path("conditions");
        if (!conditions.isArray() || conditions.isEmpty()) {
            throw NodeExecutionException.invalidConfig("'conditions' must be a non-empty list");
        }
        String combinator = config.path("combinator").asText("AND");
        if (!combinator.equals("AND") && !combinator.equals("OR")) {
            throw NodeExecutionException.invalidConfig("combinator must be AND or OR");
        }
        ArrayNode evaluations = JsonNodeFactory.instance.arrayNode();
        boolean result = combinator.equals("AND");
        for (JsonNode condition : conditions) {
            ConditionOperator operator = operator(condition.path("operator").asText());
            boolean outcome = operator.test(condition.get("left"), condition.get("right"));
            ObjectNode evaluation =
                    evaluations.addObject().put("operator", operator.name()).put("result", outcome);
            evaluation.set("left", condition.get("left"));
            evaluation.set("right", condition.get("right"));
            result = combinator.equals("AND") ? result && outcome : result || outcome;
        }
        ObjectNode output = JsonNodeFactory.instance.objectNode().put("result", result);
        output.set("evaluations", evaluations);
        return NodeResult.branch(output, result);
    }

    private static ConditionOperator operator(String name) {
        try {
            return ConditionOperator.valueOf(name);
        } catch (IllegalArgumentException e) {
            throw NodeExecutionException.invalidConfig("Unsupported operator '" + name + "'");
        }
    }
}
