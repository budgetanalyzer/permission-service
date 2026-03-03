package org.budgetanalyzer.permission.api.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.domain.Role;

/** Response DTO for role data. */
public record RoleResponse(
    @Schema(description = "Role identifier", example = "ADMIN") String id,
    @Schema(description = "Human-readable role name", example = "Administrator") String name,
    @Schema(description = "Role description", example = "Full access to all resources")
        String description,
    @Schema(description = "When the role was created", example = "2024-01-15T10:30:00Z")
        Instant createdAt) {

  public static RoleResponse from(Role role) {
    return new RoleResponse(
        role.getId(), role.getName(), role.getDescription(), role.getCreatedAt());
  }
}
