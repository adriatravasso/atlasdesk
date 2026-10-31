package com.adria.atlasdesk.organization;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import com.adria.atlasdesk.security.PasswordEncodingConfiguration;
import com.adria.atlasdesk.user.User;
import com.adria.atlasdesk.user.UserRepository;
import com.adria.atlasdesk.user.UserRole;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PasswordEncodingConfiguration.class)
class OrganizationUserPersistenceTest {

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private UserRepository users;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void persistsOrganizationAndUsersWithinTheirOrganization() {
        Organization firstOrganization = organizations.saveAndFlush(
                new Organization("Northwind", true, 96, 36, 6));
        Organization secondOrganization = organizations.saveAndFlush(
                new Organization("Contoso", false, 72, 24, 4));
        String passwordHash = passwordEncoder.encode("local-test-password");

        User firstUser = users.saveAndFlush(new User(firstOrganization, "Ada Agent", "ada@example.test",
                passwordHash, UserRole.AGENT));
        users.saveAndFlush(new User(secondOrganization, "Sam Requester", "sam@example.test",
                passwordHash, UserRole.REQUESTER));

        assertThat(organizations.findById(firstOrganization.getId()))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.isRequireApproval()).isTrue();
                    assertThat(saved.getSlaLowHours()).isEqualTo(96);
                    assertThat(saved.getCreatedAt()).isNotNull();
                    assertThat(saved.getUpdatedAt()).isNotNull();
                });
        assertThat(users.findAllByOrganizationId(firstOrganization.getId()))
                .extracting(User::getId)
                .containsExactly(firstUser.getId());
        assertThat(users.findByOrganizationIdAndEmailIgnoreCase(firstOrganization.getId(), "ADA@EXAMPLE.TEST"))
                .get()
                .satisfies(saved -> {
                    assertThat(saved.getRole()).isEqualTo(UserRole.AGENT);
                    assertThat(saved.isActive()).isTrue();
                    assertThat(saved.getPasswordHash()).isNotEqualTo("local-test-password");
                    assertThat(passwordEncoder.matches("local-test-password", saved.getPasswordHash())).isTrue();
                });
    }

    @Test
    void rejectsCaseInsensitiveDuplicateEmailWithinAnOrganization() {
        Organization organization = organizations.saveAndFlush(new Organization("Fabrikam", false, 72, 24, 4));
        String hash = passwordEncoder.encode("local-test-password");
        users.saveAndFlush(new User(organization, "First User", "user@example.test", hash, UserRole.ADMIN));

        assertThatThrownBy(() -> users.saveAndFlush(
                new User(organization, "Duplicate User", "USER@example.test", hash, UserRole.REQUESTER)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void rejectsNonPositiveOrganizationSlaValues() {
        assertThatThrownBy(() -> organizations.saveAndFlush(new Organization("Invalid SLA", false, 0, 24, 4)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }
}
