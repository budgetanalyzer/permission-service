package org.budgetanalyzer.permission.api.request;

import java.time.Instant;

import org.springframework.format.annotation.DateTimeFormat;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.domain.UserStatus;

/** Filter object for querying users based on one or more criteria. */
@Schema(description = "Filter for querying users by various fields")
public record UserFilter(
    @Schema(description = "Unique identifier for the user", example = "usr_abc123") String id,
    @Schema(
            description = "Email address (case-insensitive LIKE, multi-word OR)",
            example = "admin@example.com")
        String email,
    @Schema(
            description = "Display name (case-insensitive LIKE, multi-word OR)",
            example = "Admin User")
        String displayName,
    @Schema(
            description = "Identity provider subject identifier (exact match)",
            example = "auth0|67fd70c38eb9d43f1c93ea44")
        String idpSub,
    @Schema(
            description = "Current user status",
            allowableValues = {"ACTIVE", "DEACTIVATED"},
            example = "ACTIVE")
        UserStatus status,
    @Schema(description = "Start of creation timestamp range", example = "2026-04-01T00:00:00Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant createdAfter,
    @Schema(description = "End of creation timestamp range", example = "2026-04-08T00:00:00Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant createdBefore,
    @Schema(description = "Start of last update timestamp range", example = "2026-04-01T00:00:00Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant updatedAfter,
    @Schema(description = "End of last update timestamp range", example = "2026-04-08T00:00:00Z")
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        Instant updatedBefore) {

  /** Creates an empty filter with all criteria set to null. */
  public static UserFilter empty() {
    return new UserFilter(null, null, null, null, null, null, null, null, null);
  }
}
