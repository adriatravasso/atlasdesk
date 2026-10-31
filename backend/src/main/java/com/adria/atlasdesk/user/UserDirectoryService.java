package com.adria.atlasdesk.user;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import com.adria.atlasdesk.security.CurrentUserContext;

@Service
public class UserDirectoryService {

    private final UserRepository users;
    private final CurrentUserContext currentUser;
    private final OrganizationRepository organizations;
    private final PasswordEncoder passwordEncoder;

    public UserDirectoryService(UserRepository users, CurrentUserContext currentUser,
            OrganizationRepository organizations, PasswordEncoder passwordEncoder) {
        this.users = users;
        this.currentUser = currentUser;
        this.organizations = organizations;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public Page<UserSummary> findAllInCurrentOrganization(Pageable pageable) {
        Long organizationId = currentUser.organizationId();
        return users.findAllByOrganizationId(organizationId, pageable).map(UserDirectoryService::toSummary);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserSummary createInCurrentOrganization(String name, String email, UserRole role, Boolean active,
            String password) {
        Organization organization = lockCurrentOrganization();
        String normalizedEmail = normalizeEmail(email);
        if (users.existsByOrganizationIdAndEmailIgnoreCase(organization.getId(), normalizedEmail)) {
            throw duplicateEmail();
        }

        String passwordHash = passwordEncoder.encode(validatePassword(password));
        User user = new User(organization, normalizeName(name), normalizedEmail, passwordHash, role);
        if (Boolean.FALSE.equals(active)) {
            user.deactivate();
        }
        return toSummary(saveUser(user));
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public UserSummary findByIdInCurrentOrganization(Long userId) {
        return toSummary(findUser(userId));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserSummary deactivateInCurrentOrganization(Long userId) {
        return updateUser(userId, null, null, null, false, null);
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserSummary updateInCurrentOrganization(Long userId, String name, String email, UserRole role,
            Boolean active, String password) {
        if (name == null && email == null && role == null && active == null && password == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one field must be provided");
        }
        return updateUser(userId, name, email, role, active, password);
    }

    private User findUser(Long userId) {
        Long organizationId = currentUser.organizationId();
        return users.findByIdAndOrganizationId(userId, organizationId)
                .orElseThrow(UserNotFoundException::new);
    }

    private UserSummary updateUser(Long userId, String name, String email, UserRole role, Boolean active,
            String password) {
        Organization organization = lockCurrentOrganization();
        User user = users.findByIdAndOrganizationId(userId, organization.getId())
                .orElseThrow(UserNotFoundException::new);
        String nextEmail = email == null ? user.getEmail() : normalizeEmail(email);
        if (!nextEmail.equalsIgnoreCase(user.getEmail())
                && users.existsByOrganizationIdAndEmailIgnoreCaseAndIdNot(organization.getId(), nextEmail, userId)) {
            throw duplicateEmail();
        }

        UserRole nextRole = role == null ? user.getRole() : role;
        boolean nextActive = active == null ? user.isActive() : active;
        if (user.getRole() == UserRole.ADMIN && user.isActive()
                && (nextRole != UserRole.ADMIN || !nextActive)
                && users.countByOrganizationIdAndRoleAndActiveTrue(organization.getId(), UserRole.ADMIN) <= 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "The last active administrator cannot be deactivated or demoted");
        }

        String passwordHash = password == null ? null : passwordEncoder.encode(validatePassword(password));
        user.updateProfile(name == null ? user.getName() : normalizeName(name), nextEmail,
                nextRole, nextActive, passwordHash);
        return toSummary(saveUser(user));
    }

    private Organization lockCurrentOrganization() {
        return organizations.findByIdForUpdate(currentUser.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Current organization was not found"));
    }

    private User saveUser(User user) {
        return users.saveAndFlush(user);
    }

    private static String normalizeName(String name) {
        String normalized = name.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Name must not be blank");
        }
        return normalized;
    }

    private static String normalizeEmail(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Email must not be blank");
        }
        return normalized;
    }

    private static String validatePassword(String password) {
        if (password == null || password.length() < 12
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Password must be at least 12 characters and no more than 72 UTF-8 bytes");
        }
        return password;
    }

    private static ResponseStatusException duplicateEmail() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Email already exists in the current organization");
    }

    private static UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive());
    }
}
