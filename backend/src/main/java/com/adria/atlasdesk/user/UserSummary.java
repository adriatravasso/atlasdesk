package com.adria.atlasdesk.user;

public record UserSummary(Long id, String name, String email, UserRole role, boolean active) {
}
