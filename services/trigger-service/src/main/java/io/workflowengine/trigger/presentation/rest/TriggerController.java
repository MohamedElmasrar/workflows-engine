package io.workflowengine.trigger.presentation.rest;

import io.workflowengine.platform.security.CurrentUser;
import io.workflowengine.trigger.application.port.out.DeliveryRepository;
import io.workflowengine.trigger.application.usecase.Authorization;
import io.workflowengine.trigger.application.usecase.Authorization.Caller;
import io.workflowengine.trigger.application.usecase.TriggerQueryService;
import io.workflowengine.trigger.application.usecase.TriggerRegistrationService;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class TriggerController {

    private final TriggerQueryService queries;
    private final TriggerRegistrationService registrations;
    private final Authorization authorization;

    public TriggerController(
            TriggerQueryService queries, TriggerRegistrationService registrations, Authorization authorization) {
        this.queries = queries;
        this.registrations = registrations;
        this.authorization = authorization;
    }

    @GetMapping("/api/v1/workflows/{workflowId}/triggers")
    public TriggerQueryService.TriggersView triggers(Authentication auth, @PathVariable UUID workflowId) {
        return queries.triggers(workflowId, caller(auth));
    }

    /** Returns the new signing secret once; it is stored encrypted and never shown again. */
    @PostMapping("/api/v1/workflows/{workflowId}/triggers/webhook/secret")
    public Map<String, String> rotateSecret(Authentication auth, @PathVariable UUID workflowId) {
        authorization.requireEdit(workflowId, caller(auth));
        return Map.of("secret", registrations.rotateSecret(workflowId));
    }

    @GetMapping("/api/v1/workflows/{workflowId}/trigger-deliveries")
    public List<DeliveryRepository.Delivery> deliveries(
            Authentication auth, @PathVariable UUID workflowId, @RequestParam(defaultValue = "20") int limit) {
        return queries.deliveries(workflowId, caller(auth), limit);
    }

    private static Caller caller(Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        return new Caller(user.id(), user.admin());
    }
}
