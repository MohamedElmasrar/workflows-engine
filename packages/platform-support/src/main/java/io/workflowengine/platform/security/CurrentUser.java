package io.workflowengine.platform.security;

import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/** Authenticated caller, derived from the validated JWT. */
public record CurrentUser(String id, String username, boolean admin) {

    public static CurrentUser from(Authentication authentication) {
        boolean admin = authentication.getAuthorities().stream()
                .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        String username = authentication.getName();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            String preferred = jwt.getToken().getClaimAsString("preferred_username");
            if (preferred != null) {
                username = preferred;
            }
        }
        return new CurrentUser(authentication.getName(), username, admin);
    }
}
