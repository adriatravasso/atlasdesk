package com.adria.atlasdesk.security;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import com.adria.atlasdesk.user.User;
import com.adria.atlasdesk.user.UserRepository;

@Component
public class CurrentUserContext {

    private final UserRepository users;

    public CurrentUserContext(UserRepository users) {
        this.users = users;
    }

    public AuthenticatedUser requireUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
                || !(authentication.getPrincipal() instanceof AuthenticatedUser user)) {
            throw new AuthenticationCredentialsNotFoundException("An authenticated user is required");
        }
        User current = users.findByIdAndOrganizationId(user.id(), user.organizationId())
                .orElseThrow(() -> new AuthenticationCredentialsNotFoundException("The authenticated user is unavailable"));
        if (!current.isActive() || current.getRole() != user.role()) {
            throw new AuthenticationCredentialsNotFoundException("The authenticated user is no longer active");
        }
        return user;
    }

    public Long organizationId() {
        return requireUser().organizationId();
    }
}
