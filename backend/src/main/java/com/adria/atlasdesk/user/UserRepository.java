package com.adria.atlasdesk.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    List<User> findAllByOrganizationId(Long organizationId);

    Optional<User> findByOrganizationIdAndEmailIgnoreCase(Long organizationId, String email);
}
