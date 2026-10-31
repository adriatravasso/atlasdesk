package com.adria.atlasdesk.organization;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

public record OrganizationSettings(
        @Min(1) @Max(720) int slaLowHours,
        @Min(1) @Max(720) int slaMediumHours,
        @Min(1) @Max(720) int slaHighHours) {
}
