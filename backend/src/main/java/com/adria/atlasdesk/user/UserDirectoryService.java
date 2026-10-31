package com.adria.atlasdesk.user;

import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.adria.atlasdesk.security.CurrentUserContext;

@Service
public class UserDirectoryService {

    private final UserRepository users;
    private final CurrentUserContext currentUser;

    public UserDirectoryService(UserRepository users, CurrentUserContext currentUser) {
        this.users = users;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public List<UserSummary> findAllInCurrentOrganization() {
        Long organizationId = currentUser.organizationId();
        return users.findAllByOrganizationId(organizationId).stream()
                .map(UserDirectoryService::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasRole('ADMIN')")
    public UserSummary findByIdInCurrentOrganization(Long userId) {
        return toSummary(findUser(userId));
    }

    @Transactional
    @PreAuthorize("hasRole('ADMIN')")
    public UserSummary deactivateInCurrentOrganization(Long userId) {
        User user = findUser(userId);
        user.deactivate();
        return toSummary(users.save(user));
    }

    private User findUser(Long userId) {
        Long organizationId = currentUser.organizationId();
        return users.findByIdAndOrganizationId(userId, organizationId)
                .orElseThrow(UserNotFoundException::new);
    }

    private static UserSummary toSummary(User user) {
        return new UserSummary(user.getId(), user.getName(), user.getEmail(), user.getRole(), user.isActive());
    }
}
