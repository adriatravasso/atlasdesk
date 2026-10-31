package com.adria.atlasdesk.dev;

import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;
import com.adria.atlasdesk.user.User;
import com.adria.atlasdesk.user.UserRepository;
import com.adria.atlasdesk.user.UserRole;

@Configuration
@Profile("dev")
public class DevelopmentDataSeeder {

    @Bean
    CommandLineRunner seedDevelopmentOrganization(OrganizationRepository organizations, UserRepository users,
            PasswordEncoder passwordEncoder, @Value("${app.dev.admin-password:}") String adminPassword) {
        return args -> {
            String name = "AtlasDesk Local";
            if (!organizations.existsByName(name)) {
                organizations.save(new Organization(name, false, 72, 24, 8));
            }
            if (!adminPassword.isBlank()) {
                Organization organization = organizations.findByName(name).orElseThrow();
                String email = "atlasdesk-admin@localhost.test";
                if (!users.existsByOrganizationIdAndEmailIgnoreCase(organization.getId(), email)) {
                    users.save(new User(organization, "AtlasDesk Local Admin", email,
                            passwordEncoder.encode(adminPassword), UserRole.ADMIN));
                }
            }
        };
    }
}
