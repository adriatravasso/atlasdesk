package com.adria.atlasdesk.dev;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;
import com.adria.atlasdesk.user.User;
import com.adria.atlasdesk.user.UserRepository;
import com.adria.atlasdesk.user.UserRole;

@Configuration
@Profile("prod")
public class ProductionDemoDataSeeder {

    @Bean
    CommandLineRunner seedDemoAdministrator(OrganizationRepository organizations, UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.admin-password:}") String adminPassword,
            @Value("${app.demo.admin-email:atlasdesk-demo@example.invalid}") String adminEmail,
            @Value("${app.demo.organization-name:AtlasDesk Public Demo}") String organizationName) {
        return args -> {
            if (adminPassword.isBlank()) {
                return;
            }
            if (!organizations.existsByName(organizationName)) {
                organizations.save(new Organization(organizationName, true, 72, 24, 8));
            }
            Organization organization = organizations.findByName(organizationName).orElseThrow();
            if (!users.existsByOrganizationIdAndEmailIgnoreCase(organization.getId(), adminEmail)) {
                users.save(new User(organization, "AtlasDesk Demo Admin", adminEmail,
                        passwordEncoder.encode(adminPassword), UserRole.ADMIN));
            }
        };
    }

    @Bean
    CommandLineRunner seedDemoRequester(OrganizationRepository organizations, UserRepository users,
            PasswordEncoder passwordEncoder,
            @Value("${app.demo.requester-password:}") String requesterPassword,
            @Value("${app.demo.requester-email:demo.requester@atlasdesk.test}") String requesterEmail,
            @Value("${app.demo.organization-name:AtlasDesk Public Demo}") String organizationName) {
        return args -> {
            if (requesterPassword.isBlank()) {
                return;
            }
            if (!organizations.existsByName(organizationName)) {
                organizations.save(new Organization(organizationName, true, 72, 24, 8));
            }
            Organization organization = organizations.findByName(organizationName).orElseThrow();
            if (!users.existsByOrganizationIdAndEmailIgnoreCase(organization.getId(), requesterEmail)) {
                users.save(new User(organization, "AtlasDesk Demo Requester", requesterEmail,
                        passwordEncoder.encode(requesterPassword), UserRole.REQUESTER));
            }
        };
    }
}
