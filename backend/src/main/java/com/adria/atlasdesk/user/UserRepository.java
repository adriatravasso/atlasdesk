package com.adria.atlasdesk.user;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByIdAndOrganizationId(Long id, Long organizationId);

    Page<User> findAllByOrganizationId(Long organizationId, Pageable pageable);

    Optional<User> findByOrganizationIdAndEmailIgnoreCase(Long organizationId, String email);

    boolean existsByOrganizationIdAndEmailIgnoreCase(Long organizationId, String email);

    boolean existsByOrganizationIdAndEmailIgnoreCaseAndIdNot(Long organizationId, String email, Long id);

    long countByOrganizationIdAndRoleAndActiveTrue(Long organizationId, UserRole role);
}
