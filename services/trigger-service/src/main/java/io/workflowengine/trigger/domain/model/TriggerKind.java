package io.workflowengine.trigger.domain.model;

import io.workflowengine.contracts.NodeType;

public enum TriggerKind {
    MANUAL,
    WEBHOOK,
    SCHEDULE;

    public static TriggerKind of(NodeType type) {
        return switch (type) {
            case WEBHOOK_TRIGGER -> WEBHOOK;
            case SCHEDULE_TRIGGER -> SCHEDULE;
            case MANUAL_TRIGGER -> MANUAL;
            default -> throw new IllegalArgumentException(type + " is not a trigger");
        };
    }
}
