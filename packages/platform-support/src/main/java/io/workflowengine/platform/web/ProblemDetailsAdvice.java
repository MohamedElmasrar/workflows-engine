package io.workflowengine.platform.web;

import jakarta.servlet.http.HttpServletRequest;
import java.net.URI;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Fallback RFC 9457 Problem Details for framework and unexpected errors. Services register their own advice with a
 * higher precedence for domain exceptions. Stack traces are logged, never returned.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class ProblemDetailsAdvice extends ResponseEntityExceptionHandler {

    public static ProblemDetail problem(HttpStatus status, String type, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setType(URI.create("https://workflowengine.io/problems/" + type));
        problem.setTitle(title);
        return problem;
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        ProblemDetail problem =
                problem(HttpStatus.BAD_REQUEST, "validation-error", "Invalid request", "Request validation failed");
        List<Map<String, String>> errors = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> Map.of("field", e.getField(), "message", String.valueOf(e.getDefaultMessage())))
                .toList();
        problem.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(problem);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail accessDenied(AccessDeniedException ex) {
        return problem(HttpStatus.FORBIDDEN, "forbidden", "Forbidden", "You do not have access to this resource");
    }

    @ExceptionHandler(jakarta.validation.ConstraintViolationException.class)
    public ProblemDetail constraintViolation(jakarta.validation.ConstraintViolationException ex) {
        return problem(HttpStatus.BAD_REQUEST, "validation-error", "Invalid request", ex.getMessage());
    }

    @ExceptionHandler(RequestSizeLimitFilter.PayloadTooLargeException.class)
    public ProblemDetail tooLarge(RequestSizeLimitFilter.PayloadTooLargeException ex) {
        return problem(HttpStatus.PAYLOAD_TOO_LARGE, "payload-too-large", "Payload too large", ex.getMessage());
    }

    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            org.springframework.http.converter.HttpMessageNotReadableException ex,
            HttpHeaders headers,
            HttpStatusCode status,
            WebRequest request) {
        Throwable cause = ex.getMostSpecificCause();
        if (cause instanceof RequestSizeLimitFilter.PayloadTooLargeException tooLarge) {
            return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE).body(tooLarge(tooLarge));
        }
        return ResponseEntity.badRequest()
                .body(problem(
                        HttpStatus.BAD_REQUEST,
                        "malformed-request",
                        "Malformed request",
                        "The request body could not be parsed"));
    }

    @ExceptionHandler(Exception.class)
    public ProblemDetail unexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return problem(
                HttpStatus.INTERNAL_SERVER_ERROR,
                "internal-error",
                "Internal error",
                "An unexpected error occurred. Use the correlation id to find details in the logs.");
    }
}
