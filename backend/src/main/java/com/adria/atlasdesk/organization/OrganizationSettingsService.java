package com.adria.atlasdesk.organization;

import com.adria.atlasdesk.security.CurrentUserContext;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class OrganizationSettingsService {

    private final OrganizationRepository organizations;
    private final CurrentUserContext currentUser;

    public OrganizationSettingsService(OrganizationRepository organizations, CurrentUserContext currentUser) {
        this.organizations = organizations;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'REQUESTER')")
    public OrganizationSettings get() {
        Organization organization = organizations.findById(currentUser.organizationId())
                .orElseThrow(OrganizationSettingsService::organizationNotFound);
        return toSettings(organization);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public OrganizationSettings update(OrganizationSettings settings) {
        Organization organization = organizations.findByIdForUpdate(currentUser.organizationId())
                .orElseThrow(OrganizationSettingsService::organizationNotFound);
        organization.updateSlaHours(settings.slaLowHours(), settings.slaMediumHours(), settings.slaHighHours());
        organizations.saveAndFlush(organization);
        return toSettings(organization);
    }

    private static OrganizationSettings toSettings(Organization organization) {
        return new OrganizationSettings(organization.getSlaLowHours(), organization.getSlaMediumHours(),
                organization.getSlaHighHours());
    }

    private static ResponseStatusException organizationNotFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, "Current organization was not found");
    }
}
