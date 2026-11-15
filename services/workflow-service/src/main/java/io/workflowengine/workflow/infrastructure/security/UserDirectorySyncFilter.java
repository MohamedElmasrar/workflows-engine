package io.workflowengine.workflow.infrastructure.security;

import io.workflowengine.workflow.application.port.out.UserDirectory;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Records id/username of authenticated callers so workflows can be shared by username. Each user is written once per
 * process lifetime; the set is bounded by the number of distinct users that call this instance.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UserDirectorySyncFilter extends OncePerRequestFilter {

    private final UserDirectory users;
    private final Set<String> known = ConcurrentHashMap.newKeySet();

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken token
                && !known.contains(token.getName())) {
            String username = token.getToken().getClaimAsString("preferred_username");
            if (username != null) {
                try {
                    users.remember(token.getName(), username, token.getToken().getClaimAsString("email"));
                    known.add(token.getName());
                } catch (RuntimeException e) {
                    log.warn("Could not record user {}: {}", token.getName(), e.getMessage());
                }
            }
        }
        chain.doFilter(request, response);
    }
}
