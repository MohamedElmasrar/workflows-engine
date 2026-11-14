package io.workflowengine.trigger.presentation.exception;

import static io.workflowengine.platform.web.ProblemDetailsAdvice.problem;

import io.workflowengine.trigger.domain.exception.TriggerAccessDeniedException;
import io.workflowengine.trigger.domain.exception.TriggerNotFoundException;
import io.workflowengine.trigger.domain.exception.WebhookRejectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TriggerExceptionAdvice {

    private static final Logger log = LoggerFactory.getLogger(TriggerExceptionAdvice.class);

    @ExceptionHandler(WebhookRejectedException.class)
    ProblemDetail rejected(WebhookRejectedException e) {
        log.info("Webhook rejected: {} ({})", e.reason(), e.getMessage());
        HttpStatus status = HttpStatus.valueOf(e.reason().status);
        // do not tell unauthenticated callers whether a token exists or why the signature failed
        String detail =
                switch (e.reason()) {
                    case NOT_FOUND -> "Unknown webhook";
                    case INVALID_SIGNATURE -> "Invalid or missing signature";
                    default -> e.getMessage();
                };
        return problem(
                status, "webhook-" + e.reason().name().toLowerCase().replace('_', '-'), "Webhook rejected", detail);
    }

    @ExceptionHandler(TriggerNotFoundException.class)
    ProblemDetail notFound(TriggerNotFoundException e) {
        return problem(HttpStatus.NOT_FOUND, "not-found", "Not found", e.getMessage());
    }

    @ExceptionHandler(TriggerAccessDeniedException.class)
    ProblemDetail forbidden(TriggerAccessDeniedException e) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", e.getMessage());
    }
}
