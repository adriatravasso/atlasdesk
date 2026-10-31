package com.adria.atlasdesk.asset;

import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AssetRepository extends JpaRepository<Asset, Long> {

    Optional<Asset> findByIdAndOrganizationId(Long id, Long organizationId);

    boolean existsByOrganizationIdAndAssetNumberIgnoreCase(Long organizationId, String assetNumber);

    boolean existsByOrganizationIdAndAssetNumberIgnoreCaseAndIdNot(
            Long organizationId, String assetNumber, Long id);

    @Query("""
            select asset from Asset asset
            where asset.organization.id = :organizationId
              and (:status is null or asset.status = :status)
              and (:type = '' or lower(asset.type) = :type)
              and (:search = '' or lower(asset.assetNumber) like concat('%', :search, '%')
                   or lower(asset.name) like concat('%', :search, '%'))
            """)
    Page<Asset> searchInOrganization(@Param("organizationId") Long organizationId,
            @Param("status") AssetStatus status, @Param("type") String type,
            @Param("search") String search, Pageable pageable);
}
