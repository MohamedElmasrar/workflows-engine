package io.workflowengine.execution.application.usecase;

import io.workflowengine.execution.application.port.out.ExecutionReadModel;
import io.workflowengine.execution.application.port.out.ExecutionRepository;
import io.workflowengine.execution.application.port.out.WorkflowCatalog;
import io.workflowengine.execution.application.query.ExecutionQueries.ExecutionFilter;
import io.workflowengine.execution.application.query.ExecutionQueries.ExecutionSummary;
import io.workflowengine.execution.application.query.ExecutionQueries.HistoryEntryView;
import io.workflowengine.execution.application.query.ExecutionQueries.Page;
import io.workflowengine.execution.application.query.ExecutionQueries.Stats;
import io.workflowengine.execution.domain.exception.ExecutionNotFoundException;
import io.workflowengine.execution.domain.model.Actor;
import io.workflowengine.execution.domain.model.PublishedVersion;
import io.workflowengine.execution.domain.model.WorkflowExecution;
import io.workflowengine.execution.domain.policy.ExecutionAccessPolicy;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public class ExecutionQueryService {

    private final ExecutionRepository executions;
    private final ExecutionReadModel readModel;
    private final WorkflowCatalog catalog;
    private final Clock clock;

    public ExecutionQueryService(
            ExecutionRepository executions, ExecutionReadModel readModel, WorkflowCatalog catalog, Clock clock) {
        this.executions = executions;
        this.readModel = readModel;
        this.catalog = catalog;
        this.clock = clock;
    }

    public record Detail(WorkflowExecution execution, PublishedVersion version, long lastSequence) {}

    public Detail get(Actor actor, UUID executionId) {
        WorkflowExecution execution = visible(actor, executionId);
        PublishedVersion version = catalog.version(execution.workflowId(), execution.workflowVersion()).orElseThrow();
        return new Detail(execution, version, execution.state().eventSequence());
    }

    public Page<ExecutionSummary> search(Actor actor, ExecutionFilter filter) {
        return readModel.search(filter, actor);
    }

    public List<HistoryEntryView> history(Actor actor, UUID executionId, long afterSequence, int limit) {
        visible(actor, executionId);
        return readModel.history(executionId, afterSequence, Math.min(limit, 1000));
    }

    /** Unchecked variant for an already-authorized live stream. */
    public List<HistoryEntryView> historySince(UUID executionId, long afterSequence) {
        return readModel.history(executionId, afterSequence, 500);
    }

    public boolean isTerminal(UUID executionId) {
        return readModel.status(executionId)
                .map(s -> io.workflowengine.execution.domain.model.ExecutionStatus.valueOf(s).isTerminal())
                .orElse(true);
    }

    public Stats stats(Actor actor) {
        return readModel.stats(actor, LocalDate.now(clock).atStartOfDay().toInstant(ZoneOffset.UTC));
    }

    public List<ExecutionSummary> recent(Actor actor, int limit) {
        return readModel.recent(actor, Math.min(limit, 50));
    }

    private WorkflowExecution visible(Actor actor, UUID executionId) {
        WorkflowExecution execution = executions.find(executionId)
                .orElseThrow(() -> new ExecutionNotFoundException("Execution " + executionId));
        if (!ExecutionAccessPolicy.canView(catalog.access(execution.workflowId()).orElse(null), actor)) {
            throw new ExecutionNotFoundException("Execution " + executionId);
        }
        return execution;
    }
}
