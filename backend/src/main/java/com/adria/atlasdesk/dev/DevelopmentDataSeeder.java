package com.adria.atlasdesk.dev;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;

@Configuration
@Profile("dev")
public class DevelopmentDataSeeder {

    @Bean
    CommandLineRunner seedDevelopmentOrganization(OrganizationRepository organizations) {
        return args -> {
            String name = "AtlasDesk Local";
            if (!organizations.existsByName(name)) {
                organizations.save(new Organization(name, false, 72, 24, 4));
            }
        };
    }
}
