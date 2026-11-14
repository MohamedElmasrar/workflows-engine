package io.workflowengine.worker.domain.handler;

import io.workflowengine.contracts.NodeType;
import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;

/** Resolves handlers by node type. Adding a node type means adding a handler; no switch statement to edit. */
public final class NodeHandlerRegistry {

    private final Map<NodeType, NodeHandler> handlers = new EnumMap<>(NodeType.class);

    public NodeHandlerRegistry(Collection<? extends NodeHandler> all) {
        for (NodeHandler handler : all) {
            NodeHandler previous = handlers.putIfAbsent(handler.supportedType(), handler);
            if (previous != null) {
                throw new IllegalStateException("Two handlers registered for " + handler.supportedType() + ": "
                        + previous.getClass().getSimpleName() + ", "
                        + handler.getClass().getSimpleName());
            }
        }
    }

    public Optional<NodeHandler> resolve(NodeType type) {
        return Optional.ofNullable(handlers.get(type));
    }

    public java.util.Set<NodeType> supportedTypes() {
        return handlers.keySet();
    }
}
