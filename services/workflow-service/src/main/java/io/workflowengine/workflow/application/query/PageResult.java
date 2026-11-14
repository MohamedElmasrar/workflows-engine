package io.workflowengine.workflow.application.query;

import java.util.List;
import java.util.function.Function;

public record PageResult<T>(List<T> items, long totalItems, int page, int size) {

    public <R> PageResult<R> map(Function<T, R> mapper) {
        return new PageResult<>(items.stream().map(mapper).toList(), totalItems, page, size);
    }

    public int totalPages() {
        return size == 0 ? 0 : (int) Math.ceil((double) totalItems / size);
    }
}
