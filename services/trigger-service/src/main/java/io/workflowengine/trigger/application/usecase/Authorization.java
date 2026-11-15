package io.workflowengine.trigger.application.usecase;

import io.workflowengine.trigger.application.port.out.AccessRepository;
import io.workflowengine.trigger.domain.exception.TriggerAccessDeniedException;
import io.workflowengine.trigger.domain.exception.TriggerNotFoundException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/** Role checks against the local ACL projection; same semantics as the workflow service. */
@RequiredArgsConstructor
public class Authorization {

    public record Caller(String userId, boolean admin) {}

    private final AccessRepository access;

    public String requireView(UUID workflowId, Caller caller) {
        if (caller.admin()) {
            return "OWNER";
        }
        return access.roleOf(workflowId, caller.userId())
                .orElseThrow(() -> new TriggerNotFoundException("Workflow " + workflowId + " not found"));
    }

    public void requireEdit(UUID workflowId, Caller caller) {
        String role = requireView(workflowId, caller);
        if (!role.equals("OWNER") && !role.equals("EDITOR")) {
            throw new TriggerAccessDeniedException("Only owners and editors can manage triggers");
        }
    }
}
