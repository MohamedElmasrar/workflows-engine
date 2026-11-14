package io.workflowengine.workflow.presentation.exception;

import static io.workflowengine.platform.web.ProblemDetailsAdvice.problem;

import io.workflowengine.workflow.domain.exception.InvalidWorkflowException;
import io.workflowengine.workflow.domain.exception.RevisionConflictException;
import io.workflowengine.workflow.domain.exception.WorkflowAccessDeniedException;
import io.workflowengine.workflow.domain.exception.WorkflowNotFoundException;
import io.workflowengine.workflow.domain.exception.WorkflowStateException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/** Maps domain exceptions to Problem Details responses. */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class WorkflowExceptionAdvice {

    @ExceptionHandler(WorkflowNotFoundException.class)
    ProblemDetail notFound(WorkflowNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "workflow-not-found", "Workflow not found", e.getMessage());
    }

    @ExceptionHandler(WorkflowAccessDeniedException.class)
    ProblemDetail forbidden(WorkflowAccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", e.getMessage());
    }

    @ExceptionHandler(RevisionConflictException.class)
    ProblemDetail conflict(RevisionConflictException e) {
        ProblemDetail p = problem(HttpStatus.CONFLICT, "revision-conflict", "Concurrent modification", e.getMessage());
        p.setProperty("currentRevision", e.currentRevision());
        return p;
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    ProblemDetail lockConflict(ObjectOptimisticLockingFailureException e) {
        return problem(
                HttpStatus.CONFLICT, "revision-conflict", "Concurrent modification", "The workflow was modified concurrently; reload and retry");
    }

    @ExceptionHandler(InvalidWorkflowException.class)
    ProblemDetail invalid(InvalidWorkflowException e) {
        ProblemDetail p =
                problem(HttpStatus.UNPROCESSABLE_ENTITY, "invalid-workflow", "Invalid workflow", e.getMessage());
        p.setProperty("errors", e.result().errors());
        return p;
    }

    @ExceptionHandler(WorkflowStateException.class)
    ProblemDetail state(WorkflowStateException e) {
        return problem(HttpStatus.CONFLICT, "invalid-state", "Operation not allowed", e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Bad request", e.getMessage());
    }
}
