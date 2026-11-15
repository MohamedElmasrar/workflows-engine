package io.workflowengine.worker.domain.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.workflowengine.contracts.NodeType;
import io.workflowengine.worker.domain.model.NodeExecutionContext;
import io.workflowengine.worker.domain.model.NodeExecutionException;
import io.workflowengine.worker.domain.model.NodeResult;
import java.time.Clock;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Writes a user-defined message to the worker log (with execution context in the MDC) and echoes it as output. */
@RequiredArgsConstructor
public class LogHandler implements NodeHandler {

    private static final Logger log = LoggerFactory.getLogger("workflow.node.log");
    private static final int MAX_MESSAGE = 4000;

    private final Clock clock;

    @Override
    public NodeType supportedType() {
        return NodeType.LOG;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context, JsonNode config) {
        JsonNode messageNode = config.get("message");
        if (messageNode == null || messageNode.isNull()) {
            throw NodeExecutionException.invalidConfig("'message' is required");
        }
        String message = messageNode.isTextual() ? messageNode.asText() : messageNode.toString();
        if (message.length() > MAX_MESSAGE) {
            message = message.substring(0, MAX_MESSAGE);
        }
        String level = config.path("level").asText("INFO");
        switch (level) {
            case "WARN" -> log.warn("{}", message);
            case "ERROR" -> log.error("{}", message);
            case "INFO" -> log.info("{}", message);
            default -> throw NodeExecutionException.invalidConfig("Unsupported log level " + level);
        }
        return NodeResult.success(JsonNodeFactory.instance
                .objectNode()
                .put("level", level)
                .put("message", message)
                .put("loggedAt", clock.instant().toString()));
    }
}
