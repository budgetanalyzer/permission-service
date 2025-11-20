package org.budgetanalyzer.permission.service.dto;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Filter criteria for querying audit logs.
 *
 * <p>Built by controller from query parameters, used by AuditService.
 */
@Schema(description = "Internal DTO for audit log query filters")
public record AuditQueryFilter(
    @Schema(description = "Filter by user ID", example = "usr_abc123") String userId,
    @Schema(description = "Start of time range", example = "2024-01-01T00:00:00Z")
        Instant startTime,
    @Schema(description = "End of time range", example = "2024-12-31T23:59:59Z") Instant endTime) {}
