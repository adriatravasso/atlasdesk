package com.adria.atlasdesk.asset;

import java.util.Locale;
import com.adria.atlasdesk.organization.Organization;
import com.adria.atlasdesk.organization.OrganizationRepository;
import com.adria.atlasdesk.security.CurrentUserContext;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class AssetDirectoryService {

    private final AssetRepository assets;
    private final OrganizationRepository organizations;
    private final CurrentUserContext currentUser;

    public AssetDirectoryService(AssetRepository assets, OrganizationRepository organizations,
            CurrentUserContext currentUser) {
        this.assets = assets;
        this.organizations = organizations;
        this.currentUser = currentUser;
    }

    @Transactional(readOnly = true)
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT', 'REQUESTER')")
    public Page<AssetSummary> findAllInCurrentOrganization(AssetStatus status, String type, String search,
            Pageable pageable) {
        Long organizationId = currentUser.organizationId();
        return assets.searchInOrganization(organizationId, status, normalizeFilter(type), normalizeFilter(search),
                pageable).map(AssetDirectoryService::toSummary);
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public AssetSummary createInCurrentOrganization(String assetNumber, String name, String type,
            String description, AssetStatus status) {
        Organization organization = lockCurrentOrganization();
        String normalizedNumber = normalizeRequired(assetNumber, "Asset number");
        if (assets.existsByOrganizationIdAndAssetNumberIgnoreCase(organization.getId(), normalizedNumber)) {
            throw duplicateAssetNumber();
        }
        Asset asset = new Asset(organization, normalizedNumber, normalizeRequired(name, "Name"),
                normalizeRequired(type, "Type"), normalizeDescription(description),
                status == null ? AssetStatus.ACTIVE : status);
        return toSummary(assets.saveAndFlush(asset));
    }

    @Transactional
    @PreAuthorize("hasAnyRole('ADMIN', 'AGENT')")
    public AssetSummary updateInCurrentOrganization(Long assetId, String assetNumber, String name, String type,
            String description, AssetStatus status) {
        if (assetNumber == null && name == null && type == null && description == null && status == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "At least one field must be provided");
        }

        Organization organization = lockCurrentOrganization();
        Asset asset = assets.findByIdAndOrganizationId(assetId, organization.getId())
                .orElseThrow(AssetNotFoundException::new);
        String nextNumber = assetNumber == null
                ? asset.getAssetNumber()
                : normalizeRequired(assetNumber, "Asset number");
        if (!nextNumber.equalsIgnoreCase(asset.getAssetNumber())
                && assets.existsByOrganizationIdAndAssetNumberIgnoreCaseAndIdNot(
                        organization.getId(), nextNumber, assetId)) {
            throw duplicateAssetNumber();
        }
        asset.updateProfile(nextNumber,
                name == null ? asset.getName() : normalizeRequired(name, "Name"),
                type == null ? asset.getType() : normalizeRequired(type, "Type"),
                description == null ? asset.getDescription() : normalizeDescription(description),
                status == null ? asset.getStatus() : status);
        return toSummary(assets.saveAndFlush(asset));
    }

    private Organization lockCurrentOrganization() {
        return organizations.findByIdForUpdate(currentUser.organizationId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Current organization was not found"));
    }

    private static String normalizeRequired(String value, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " must not be blank");
        }
        return normalized;
    }

    private static String normalizeDescription(String description) {
        if (description == null || description.trim().isEmpty()) {
            return null;
        }
        return description.trim();
    }

    private static String normalizeFilter(String value) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    private static ResponseStatusException duplicateAssetNumber() {
        return new ResponseStatusException(HttpStatus.CONFLICT,
                "Asset number already exists in the current organization");
    }

    private static AssetSummary toSummary(Asset asset) {
        return new AssetSummary(asset.getId(), asset.getOrganization().getId(), asset.getAssetNumber(),
                asset.getName(), asset.getType(), asset.getDescription(), asset.getStatus(),
                asset.getCreatedAt(), asset.getUpdatedAt());
    }
}
