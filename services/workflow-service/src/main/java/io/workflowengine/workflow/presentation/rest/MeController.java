package io.workflowengine.workflow.presentation.rest;

import io.workflowengine.platform.security.CurrentUser;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class MeController {

    public record MeResponse(String id, String username, boolean admin, List<String> roles) {}

    @GetMapping("/api/v1/me")
    public MeResponse me(Authentication auth) {
        CurrentUser user = CurrentUser.from(auth);
        List<String> roles = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .map(a -> a.replaceFirst("^ROLE_", ""))
                .toList();
        return new MeResponse(user.id(), user.username(), user.admin(), roles);
    }
}
