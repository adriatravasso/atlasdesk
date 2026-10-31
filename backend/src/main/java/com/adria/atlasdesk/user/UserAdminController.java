package com.adria.atlasdesk.user;

import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
@RequestMapping("/api/users")
public class UserAdminController {

    private final UserDirectoryService users;

    public UserAdminController(UserDirectoryService users) {
        this.users = users;
    }

    @GetMapping
    public UserPageResponse list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        Page<UserSummary> result = users.findAllInCurrentOrganization(PageRequest.of(page, size,
                Sort.by(Sort.Order.asc("name"), Sort.Order.asc("id"))));
        return new UserPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @PostMapping
    public ResponseEntity<UserSummary> create(@Valid @RequestBody CreateUserRequest request) {
        UserSummary user = users.createInCurrentOrganization(request.name(), request.email(), request.role(),
                request.active(), request.password());
        return ResponseEntity.status(HttpStatus.CREATED).body(user);
    }

    @PatchMapping("/{id}")
    public UserSummary update(@PathVariable @Positive Long id, @Valid @RequestBody PatchUserRequest request) {
        return users.updateInCurrentOrganization(id, request.name(), request.email(), request.role(),
                request.active(), request.password());
    }

    public record CreateUserRequest(@NotBlank @Size(max = 160) String name,
            @NotBlank @Email @Size(max = 320) String email,
            @NotNull UserRole role,
            Boolean active,
            @NotBlank @Size(min = 12, max = 72) String password) {
    }

    public record PatchUserRequest(@Size(max = 160) String name,
            @Email @Size(max = 320) String email,
            UserRole role,
            Boolean active,
            @Size(min = 12, max = 72) String password) {
    }

    public record UserPageResponse(List<UserSummary> content, int page, int size, long totalElements, int totalPages) {
    }
}
