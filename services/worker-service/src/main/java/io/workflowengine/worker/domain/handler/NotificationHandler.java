package io.workflowengine.worker.domain.handler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import io.workflowengine.contracts.NodeType;
import io.workflowengine.worker.domain.model.NodeExecutionContext;
import io.workflowengine.worker.domain.model.NodeExecutionException;
import io.workflowengine.worker.domain.model.NodeResult;
import io.workflowengine.worker.domain.port.NotificationChannel;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Sends a notification through the configured channel adapter (Adapter pattern; new channels plug in as beans). */
public class NotificationHandler implements NodeHandler {

    private final Map<String, NotificationChannel> channels;
    private final Clock clock;

    public NotificationHandler(List<NotificationChannel> channels, Clock clock) {
        this.channels = channels.stream().collect(Collectors.toMap(NotificationChannel::channel, Function.identity()));
        this.clock = clock;
    }

    @Override
    public NodeType supportedType() {
        return NodeType.NOTIFICATION;
    }

    @Override
    public NodeResult execute(NodeExecutionContext context, JsonNode config) {
        String channelName = config.path("channel").asText("");
        NotificationChannel channel = channels.get(channelName);
        if (channel == null) {
            throw NodeExecutionException.invalidConfig("Unsupported notification channel '" + channelName + "'");
        }
        String recipient = config.path("recipient").asText("").strip();
        String problem = channel.recipientProblem(recipient);
        if (problem != null) {
            throw NodeExecutionException.invalidConfig(problem);
        }
        String message = config.path("message").asText("");
        if (message.isBlank()) {
            throw NodeExecutionException.invalidConfig("'message' is required");
        }
        String subject = config.path("subject").asText("Workflow notification");
        channel.send(new NotificationChannel.Notification(recipient, subject, message, context.idempotencyKey()));
        return NodeResult.success(JsonNodeFactory.instance.objectNode()
                .put("channel", channelName)
                .put("recipient", recipient)
                .put("subject", subject)
                .put("deliveredAt", clock.instant().toString()));
    }
}
