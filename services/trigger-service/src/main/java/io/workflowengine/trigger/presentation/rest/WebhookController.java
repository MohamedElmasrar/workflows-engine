package io.workflowengine.trigger.presentation.rest;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.workflowengine.trigger.application.usecase.WebhookService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Public, unauthenticated endpoint: the unguessable token (and optional HMAC signature) authenticate the caller. */
@RestController
@Tag(name = "Webhooks")
public class WebhookController {

    private final WebhookService webhooks;

    public WebhookController(WebhookService webhooks) {
        this.webhooks = webhooks;
    }

    public record WebhookAccepted(String deliveryId, String executionId, boolean duplicate) {}

    @PostMapping("/api/v1/webhooks/{token}")
    @Operation(summary = "Receive a webhook and start the bound workflow (202 Accepted)")
    public ResponseEntity<WebhookAccepted> receive(
            @PathVariable String token, @RequestBody(required = false) byte[] body, HttpServletRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        for (String name : Collections.list(request.getHeaderNames())) {
            headers.put(name, request.getHeader(name));
        }
        Map<String, String> query = new LinkedHashMap<>();
        request.getParameterMap().forEach((k, v) -> query.put(k, v.length == 0 ? "" : v[0]));
        WebhookService.Accepted accepted = webhooks.receive(new WebhookService.Incoming(
                token, request.getContentType(), headers, query, body == null ? new byte[0] : body));
        return ResponseEntity.status(accepted.duplicate() ? HttpStatus.OK : HttpStatus.ACCEPTED)
                .body(new WebhookAccepted(
                        accepted.deliveryId().toString(), accepted.executionId().toString(), accepted.duplicate()));
    }
}
