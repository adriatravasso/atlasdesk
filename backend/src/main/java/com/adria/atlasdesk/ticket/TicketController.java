package com.adria.atlasdesk.ticket;

import java.util.List;
import jakarta.validation.Valid;
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
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/tickets")
public class TicketController {

    private final TicketDirectoryService tickets;

    public TicketController(TicketDirectoryService tickets) {
        this.tickets = tickets;
    }

    @PostMapping
    public ResponseEntity<TicketSummary> create(@Valid @RequestBody CreateTicketRequest request) {
        TicketSummary created = tickets.create(request.title(), request.description(), request.priority(),
                request.assetId());
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping
    public TicketPageResponse list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size,
            @RequestParam(required = false) @Size(max = 160) String search,
            @RequestParam(required = false) TicketStatus status,
            @RequestParam(required = false) TicketPriority priority) {
        Page<TicketSummary> result = tickets.list(status, priority, search,
                PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new TicketPageResponse(result.getContent(), result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    @GetMapping("/{id}")
    public TicketSummary get(@PathVariable @Positive Long id) {
        return tickets.get(id);
    }

    public record CreateTicketRequest(@NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 4000) String description,
            @NotNull TicketPriority priority,
            @Positive Long assetId) {
    }

    public record TicketPageResponse(List<TicketSummary> content, int page, int size,
            long totalElements, int totalPages) {
    }
}
