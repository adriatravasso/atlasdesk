package com.adria.atlasdesk.asset;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/assets")
public class AssetController {

    private final AssetDirectoryService assets;

    public AssetController(AssetDirectoryService assets) {
        this.assets = assets;
    }

    @GetMapping
    public AssetPageResponse list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 160) String search,
            @RequestParam(required = false) @Size(max = 80) String type,
            @RequestParam(required = false) AssetStatus status) {
        Page<AssetSummary> result = assets.findAllInCurrentOrganization(status, type, search,
                PageRequest.of(page, size, Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))));
        return new AssetPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping
    public ResponseEntity<AssetSummary> create(@Valid @RequestBody CreateAssetRequest request) {
        AssetSummary asset = assets.createInCurrentOrganization(request.assetNumber(), request.name(), request.type(),
                request.description(), request.status());
        return ResponseEntity.status(HttpStatus.CREATED).body(asset);
    }

    @PatchMapping("/{id}")
    public AssetSummary update(@PathVariable @Positive Long id, @Valid @RequestBody PatchAssetRequest request) {
        return assets.updateInCurrentOrganization(id, request.assetNumber(), request.name(), request.type(),
                request.description(), request.status());
    }

    public record CreateAssetRequest(@NotBlank @Size(max = 80) String assetNumber,
            @NotBlank @Size(max = 160) String name,
            @NotBlank @Size(max = 80) String type,
            @Size(max = 2000) String description,
            AssetStatus status) {
    }

    public record PatchAssetRequest(@Size(max = 80) String assetNumber,
            @Size(max = 160) String name,
            @Size(max = 80) String type,
            @Size(max = 2000) String description,
            AssetStatus status) {
    }

    public record AssetPageResponse(List<AssetSummary> content, int page, int size,
            long totalElements, int totalPages) {
    }
}
