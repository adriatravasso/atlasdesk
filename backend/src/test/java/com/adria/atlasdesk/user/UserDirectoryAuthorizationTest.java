package com.adria.atlasdesk.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;
import com.adria.atlasdesk.security.AuthenticatedUser;
import com.adria.atlasdesk.security.CurrentUserContext;

@SpringBootTest
@Transactional
class UserDirectoryAuthorizationTest {

    @Autowired
    private OrganizationRepository organizations;

    @Autowired
    private UserRepository users;

    @Autowired
    private UserDirectoryService userDirectory;

    @Autowired
    private CurrentUserContext currentUser;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void adminCanReadOnlyUsersFromCurrentOrganization() {
        Organization currentOrganization = createOrganization("Current");
        User currentUserRecord = createUser(currentOrganization, UserRole.ADMIN);
        User secondCurrentOrganizationUser = createUser(currentOrganization, UserRole.AGENT);
        User foreignUser = createUser(createOrganization("Foreign"), UserRole.REQUESTER);
        authenticateAs(currentUserRecord, UserRole.ADMIN);

        assertThat(currentUser.organizationId()).isEqualTo(currentOrganization.getId());
        assertThat(userDirectory.findAllInCurrentOrganization(PageRequest.of(0, 20)).getContent())
                .extracting(UserSummary::id)
                .containsExactlyInAnyOrder(currentUserRecord.getId(), secondCurrentOrganizationUser.getId())
                .doesNotContain(foreignUser.getId());
        assertThat(userDirectory.findByIdInCurrentOrganization(currentUserRecord.getId()).email())
                .isEqualTo(currentUserRecord.getEmail());
    }

    @Test
    void agentsAndRequestersCannotUseAdminUserDirectoryOperations() {
        Organization organization = createOrganization("Role checks");
        User admin = createUser(organization, UserRole.ADMIN);
        User target = createUser(organization, UserRole.REQUESTER);

        for (UserRole role : List.of(UserRole.AGENT, UserRole.REQUESTER)) {
            authenticateAs(admin, role);
            assertThatThrownBy(() -> userDirectory.findAllInCurrentOrganization(PageRequest.of(0, 20)))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> userDirectory.findByIdInCurrentOrganization(target.getId()))
                    .isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(() -> userDirectory.deactivateInCurrentOrganization(target.getId()))
                    .isInstanceOf(AccessDeniedException.class);
        }
    }

    @Test
    void foreignUserIsNotFoundAndCannotBeChanged() {
        User currentUserRecord = createUser(createOrganization("Current owner"), UserRole.ADMIN);
        User foreignUser = createUser(createOrganization("Other owner"), UserRole.AGENT);
        authenticateAs(currentUserRecord, UserRole.ADMIN);

        assertThatThrownBy(() -> userDirectory.findByIdInCurrentOrganization(foreignUser.getId()))
                .isInstanceOf(UserNotFoundException.class);
        assertThatThrownBy(() -> userDirectory.deactivateInCurrentOrganization(foreignUser.getId()))
                .isInstanceOf(UserNotFoundException.class);
        assertThat(users.findById(foreignUser.getId()).orElseThrow().isActive()).isTrue();
    }

    @Test
    void adminCanDeactivateAUserInTheSameOrganization() {
        Organization organization = createOrganization("Own organization");
        User admin = createUser(organization, UserRole.ADMIN);
        User target = createUser(organization, UserRole.AGENT);
        authenticateAs(admin, UserRole.ADMIN);

        UserSummary updated = userDirectory.deactivateInCurrentOrganization(target.getId());

        assertThat(updated.active()).isFalse();
        assertThat(users.findByIdAndOrganizationId(target.getId(), organization.getId()).orElseThrow().isActive())
                .isFalse();
    }

    @Test
    void currentUserContextRequiresAnAuthenticatedPrincipal() {
        assertThatThrownBy(currentUser::requireUser)
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private Organization createOrganization(String prefix) {
        return organizations.saveAndFlush(new Organization(prefix + " " + UUID.randomUUID(), false, 72, 24, 4));
    }

    private User createUser(Organization organization, UserRole role) {
        String email = role.name().toLowerCase() + "-" + UUID.randomUUID() + "@example.test";
        return users.saveAndFlush(new User(organization, "Test " + role, email,
                passwordEncoder.encode("temporary-test-password"), role));
    }

    private void authenticateAs(User user, UserRole role) {
        AuthenticatedUser principal = new AuthenticatedUser(user.getId(), user.getOrganization().getId(),
                user.getName(), user.getEmail(), role);
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                principal, null, List.of(new SimpleGrantedAuthority("ROLE_" + role.name()))));
        SecurityContextHolder.setContext(context);
    }
}
