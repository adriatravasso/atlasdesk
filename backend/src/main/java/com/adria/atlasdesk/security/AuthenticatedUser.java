package com.adria.atlasdesk.security;

import com.adria.atlasdesk.user.UserRole;

public record AuthenticatedUser(Long id, Long organizationId, String name, String email, UserRole role) {
}
