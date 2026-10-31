package com.adria.atlasdesk.organization;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/settings")
public class OrganizationSettingsController {

    private final OrganizationSettingsService settings;

    public OrganizationSettingsController(OrganizationSettingsService settings) {
        this.settings = settings;
    }

    @GetMapping
    public OrganizationSettings get() {
        return settings.get();
    }

    @PatchMapping
    public OrganizationSettings update(@Valid @RequestBody OrganizationSettings settings) {
        return this.settings.update(settings);
    }
}
