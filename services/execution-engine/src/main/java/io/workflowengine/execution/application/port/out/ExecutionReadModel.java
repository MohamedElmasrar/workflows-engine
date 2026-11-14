package io.workflowengine.execution.application.port.out;

import io.workflowengine.execution.application.query.ExecutionQueries.ExecutionFilter;
import io.workflowengine.execution.application.query.ExecutionQueries.ExecutionSummary;
import io.workflowengine.execution.application.query.ExecutionQueries.HistoryEntryView;
import io.workflowengine.execution.application.query.ExecutionQueries.Page;
import io.workflowengine.execution.application.query.ExecutionQueries.Stats;
import io.workflowengine.execution.domain.model.Actor;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ExecutionReadModel {

    Page<ExecutionSummary> search(ExecutionFilter filter, Actor actor);

    List<HistoryEntryView> history(UUID executionId, long afterSequence, int limit);

    Stats stats(Actor actor, Instant since);

    List<ExecutionSummary> recent(Actor actor, int limit);

    java.util.Optional<String> status(UUID executionId);
}
