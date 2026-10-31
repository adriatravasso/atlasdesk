package com.adria.atlasdesk.security;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.authentication.AbstractAuthenticationToken;

public final class OrganizationLoginAuthenticationToken extends AbstractAuthenticationToken implements CredentialsContainer {

    private final Long organizationId;
    private final String email;
    private String password;

    public OrganizationLoginAuthenticationToken(Long organizationId, String email, String password) {
        super(null);
        this.organizationId = organizationId;
        this.email = email;
        this.password = password;
        setAuthenticated(false);
    }

    public Long getOrganizationId() {
        return organizationId;
    }

    public String getEmail() {
        return email;
    }

    @Override
    public Object getCredentials() {
        return password;
    }

    @Override
    public Object getPrincipal() {
        return email;
    }

    @Override
    public void eraseCredentials() {
        password = null;
    }
}
