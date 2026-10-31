package com.adria.atlasdesk.dev;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.boot.CommandLineRunner;
import com.adria.atlasdesk.organization.OrganizationRepository;

class DevelopmentDataSeederTest {

    private final DevelopmentDataSeeder seeder = new DevelopmentDataSeeder();

    @Test
    void seedsOnlyTheLocalOrganizationWhenItDoesNotExist() throws Exception {
        OrganizationRepository organizations = org.mockito.Mockito.mock(OrganizationRepository.class);
        when(organizations.existsByName("AtlasDesk Local")).thenReturn(false);

        CommandLineRunner runner = seeder.seedDevelopmentOrganization(organizations);
        runner.run();

        verify(organizations).save(org.mockito.ArgumentMatchers.argThat(organization ->
                organization.getName().equals("AtlasDesk Local")
                        && !organization.isRequireApproval()
                        && organization.getSlaLowHours() == 72
                        && organization.getSlaMediumHours() == 24
                        && organization.getSlaHighHours() == 4));
    }

    @Test
    void doesNotDuplicateTheLocalOrganization() throws Exception {
        OrganizationRepository organizations = org.mockito.Mockito.mock(OrganizationRepository.class);
        when(organizations.existsByName("AtlasDesk Local")).thenReturn(true);

        seeder.seedDevelopmentOrganization(organizations).run();

        verify(organizations).existsByName("AtlasDesk Local");
        verifyNoMoreInteractions(organizations);
    }
}
