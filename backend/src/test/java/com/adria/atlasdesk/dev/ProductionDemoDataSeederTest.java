package com.adria.atlasdesk.dev;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;
import com.adria.atlasdesk.user.User;
import com.adria.atlasdesk.user.UserRepository;

class ProductionDemoDataSeederTest {

    private final ProductionDemoDataSeeder seeder = new ProductionDemoDataSeeder();

    @Test
    void doesNotCreateDemoDataWithoutASecretPassword() throws Exception {
        OrganizationRepository organizations = org.mockito.Mockito.mock(OrganizationRepository.class);
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);

        CommandLineRunner runner = seeder.seedDemoAdministrator(
                organizations, users, encoder, "", "demo@example.invalid", "AtlasDesk Public Demo");
        runner.run();

        verify(organizations, never()).save(org.mockito.ArgumentMatchers.any(Organization.class));
        verify(users, never()).save(org.mockito.ArgumentMatchers.any(User.class));
    }

    @Test
    void createsOnlyTheConfiguredSyntheticDemoAdministrator() throws Exception {
        OrganizationRepository organizations = org.mockito.Mockito.mock(OrganizationRepository.class);
        UserRepository users = org.mockito.Mockito.mock(UserRepository.class);
        PasswordEncoder encoder = org.mockito.Mockito.mock(PasswordEncoder.class);
        Organization organization = org.mockito.Mockito.mock(Organization.class);
        when(organizations.existsByName("AtlasDesk Public Demo")).thenReturn(false);
        when(organizations.findByName("AtlasDesk Public Demo")).thenReturn(Optional.of(organization));
        when(organization.getId()).thenReturn(1L);
        when(users.existsByOrganizationIdAndEmailIgnoreCase(1L, "demo-admin@example.invalid"))
                .thenReturn(false);
        when(encoder.encode("synthetic-demo-password")).thenReturn("bcrypt-hash");

        seeder.seedDemoAdministrator(organizations, users, encoder,
                "synthetic-demo-password", "demo-admin@example.invalid", "AtlasDesk Public Demo").run();

        verify(users).save(org.mockito.ArgumentMatchers.argThat(user ->
                user.getOrganization() == organization
                        && user.getName().equals("AtlasDesk Demo Admin")
                        && user.getEmail().equals("demo-admin@example.invalid")
                        && user.getPasswordHash().equals("bcrypt-hash")
                        && user.getRole().name().equals("ADMIN")
                        && user.isActive()));
    }
}
