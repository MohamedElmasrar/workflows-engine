package io.workflowengine.worker.domain.model;

/** Classified failure raised by handlers; converted into {@link NodeResult.Failure}. */
public class NodeExecutionException extends RuntimeException {

    public static final String INVALID_CONFIGURATION = "INVALID_CONFIGURATION";

    private final String code;
    private final boolean retryable;

    public NodeExecutionException(String code, String message, boolean retryable) {
        super(message);
        this.code = code;
        this.retryable = retryable;
    }

    public NodeExecutionException(String code, String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.retryable = retryable;
    }

    public static NodeExecutionException invalidConfig(String message) {
        return new NodeExecutionException(INVALID_CONFIGURATION, message, false);
    }

    public String code() {
        return code;
    }

    public boolean retryable() {
        return retryable;
    }

    public NodeResult.Failure toFailure() {
        return new NodeResult.Failure(code, getMessage(), retryable);
    }
}
