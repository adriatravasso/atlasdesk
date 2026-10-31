package com.adria.atlasdesk.asset;

import java.time.Instant;

public record AssetSummary(Long id, Long organizationId, String assetNumber, String name, String type,
        String description, AssetStatus status, Instant createdAt, Instant updatedAt) {
}
