package com.adria.atlasdesk.dev;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import com.adria.atlasdesk.organization.OrganizationRepository;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.user.UserRepository;
import com.adria.atlasdesk.user.User;
import org.springframework.security.crypto.password.PasswordEncoder;
import java.util.Optional;

class DevelopmentDataSeederTest {

    private final DevelopmentDataSeeder seeder = new DevelopmentDataSeeder();

    @Test
    void seedsOnlyTheLocalOrganizationWhenItDoesNotExist() throws Exception {
        OrganizationRepository organizations = org.mockito.Mockito.mock(OrganizationRepository.class);
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        when(organizations.existsByName("AtlasDesk Local")).thenReturn(false);

        CommandLineRunner runner = seeder.seedDevelopmentOrganization(organizations, users, encoder, "");
        runner.run();

        verify(organizations).save(org.mockito.ArgumentMatchers.argThat(organization ->
                organization.getName().equals("AtlasDesk Local")
                        && !organization.isRequireApproval()
                        && organization.getSlaLowHours() == 72
                        && organization.getSlaMediumHours() == 24
                        && organization.getSlaHighHours() == 8));
        verifyNoMoreInteractions(users, encoder);
    }

    @Test
    void doesNotDuplicateTheLocalOrganization() throws Exception {
        OrganizationRepository organizations = org.mockito.Mockito.mock(OrganizationRepository.class);
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        when(organizations.existsByName("AtlasDesk Local")).thenReturn(true);

        seeder.seedDevelopmentOrganization(organizations, users, encoder, "").run();

        verify(organizations).existsByName("AtlasDesk Local");
        verifyNoMoreInteractions(organizations);
        verifyNoMoreInteractions(users, encoder);
    }

    @Test
    void seedsAnAdminOnlyWhenADevelopmentPasswordIsConfigured() throws Exception {
        OrganizationRepository organizations = org.mockito.Mockito.mock(OrganizationRepository.class);
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        Organization organization = org.mockito.Mockito.mock(Organization.class);
        when(organizations.existsByName("AtlasDesk Local")).thenReturn(true);
        when(organizations.findByName("AtlasDesk Local")).thenReturn(Optional.of(organization));
        when(organization.getId()).thenReturn(1L);
        when(users.existsByOrganizationIdAndEmailIgnoreCase(1L, "atlasdesk-admin@localhost.test"))
                .thenReturn(false);
        when(encoder.encode("local-test-password")).thenReturn("encoded-password");

        seeder.seedDevelopmentOrganization(organizations, users, encoder, "local-test-password").run();

        verify(users).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getOrganization() == organization
                        && user.getName().equals("AtlasDesk Local Admin")
                        && user.getEmail().equals("atlasdesk-admin@localhost.test")
                        && user.getPasswordHash().equals("encoded-password")
                        && user.getRole().name().equals("ADMIN")
                        && user.isActive()));
    }

    @Test
    void doesNotDuplicateTheDevelopmentAdmin() throws Exception {
        OrganizationRepository organizations = org.mockito.Mockito.mock(OrganizationRepository.class);
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        Organization organization = org.mockito.Mockito.mock(Organization.class);
        when(organizations.existsByName("AtlasDesk Local")).thenReturn(true);
        when(organizations.findByName("AtlasDesk Local")).thenReturn(Optional.of(organization));
        when(organization.getId()).thenReturn(1L);
        when(users.existsByOrganizationIdAndEmailIgnoreCase(1L, "atlasdesk-admin@localhost.test"))
                .thenReturn(true);

        seeder.seedDevelopmentOrganization(organizations, users, encoder, "configured-password").run();

        verify(users, never()).save(org.mockito.ArgumentMatchers.any(User.class));
        verifyNoMoreInteractions(encoder);
    }
}
