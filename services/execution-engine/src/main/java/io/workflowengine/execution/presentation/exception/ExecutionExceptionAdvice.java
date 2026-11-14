package io.workflowengine.execution.presentation.exception;

import static io.workflowengine.platform.web.ProblemDetailsAdvice.problem;

import io.workflowengine.execution.application.usecase.ExecutionService.VersionNotAvailableException;
import io.workflowengine.execution.domain.exception.ExecutionAccessDeniedException;
import io.workflowengine.execution.domain.exception.ExecutionNotFoundException;
import io.workflowengine.execution.domain.exception.ExecutionStateException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ExecutionExceptionAdvice {

    @ExceptionHandler(ExecutionNotFoundException.class)
    ProblemDetail notFound(ExecutionNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", e.getMessage());
    }

    @ExceptionHandler(ExecutionAccessDeniedException.class)
    ProblemDetail forbidden(ExecutionAccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", e.getMessage());
    }

    @ExceptionHandler(ExecutionStateException.class)
    ProblemDetail conflict(ExecutionStateException e) {
        return problem(HttpStatus.CONFLICT, "invalid-state", "Operation not allowed", e.getMessage());
    }

    @ExceptionHandler(VersionNotAvailableException.class)
    ProblemDetail versionMissing(VersionNotAvailableException e) {
        ProblemDetail p = problem(HttpStatus.CONFLICT, "version-not-available", "Version not available", e.getMessage());
        p.setProperty("retryable", true);
        return p;
    }

    @ExceptionHandler(IllegalArgumentException.class)
    ProblemDetail badRequest(IllegalArgumentException e) {
        return problem(HttpStatus.BAD_REQUEST, "bad-request", "Bad request", e.getMessage());
    }
}
