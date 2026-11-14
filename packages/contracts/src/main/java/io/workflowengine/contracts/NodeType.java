package io.workflowengine.contracts;

/** Node types understood by workflow definition schema version 1. */
public enum NodeType {
    MANUAL_TRIGGER(Category.TRIGGER),
    WEBHOOK_TRIGGER(Category.TRIGGER),
    SCHEDULE_TRIGGER(Category.TRIGGER),
    HTTP_REQUEST(Category.ACTION),
    TRANSFORM(Category.ACTION),
    LOG(Category.ACTION),
    NOTIFICATION(Category.ACTION),
    CONDITION(Category.LOGIC),
    DELAY(Category.LOGIC);

    public enum Category {
        TRIGGER,
        ACTION,
        LOGIC
    }

    private final Category category;

    NodeType(Category category) {
        this.category = category;
    }

    public Category category() {
        return category;
    }

    public boolean isTrigger() {
        return category == Category.TRIGGER;
    }
}
