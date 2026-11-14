package io.workflowengine.worker.domain.handler;

import com.fasterxml.jackson.databind.JsonNode;
import io.workflowengine.contracts.NodeType;
import io.workflowengine.worker.domain.model.NodeExecutionContext;
import io.workflowengine.worker.domain.model.NodeResult;

/**
 * Strategy for executing one node type. Implementations receive configuration with templates already resolved, must
 * be stateless, and signal classified failures with {@link io.workflowengine.worker.domain.model.NodeExecutionException}.
 */
public interface NodeHandler {

    NodeType supportedType();

    NodeResult execute(NodeExecutionContext context, JsonNode configuration);
}
