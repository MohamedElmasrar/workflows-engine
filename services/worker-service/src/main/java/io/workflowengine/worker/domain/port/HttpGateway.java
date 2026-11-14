package io.workflowengine.worker.domain.port;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/** Outbound HTTP, implemented by an adapter that enforces the SSRF policy, size limits and circuit breaking. */
public interface HttpGateway {

    HttpResponseData send(HttpCall call);

    record HttpCall(String method, String url, Map<String, String> headers, byte[] body, Duration timeout) {}

    record HttpResponseData(int status, Map<String, List<String>> headers, byte[] body, String contentType) {}
}
