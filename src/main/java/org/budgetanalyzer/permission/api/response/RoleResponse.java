package org.budgetanalyzer.permission.api.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.domain.Role;

/** Response DTO for role data. */
public record RoleResponse(
    @Schema(description = "Role identifier", example = "MANAGER") String id,
    @Schema(description = "Human-readable role name", example = "Manager") String name,
    @Schema(description = "Role description", example = "Team oversight and approvals")
        String description,
    @Schema(
            description = "Parent role ID for hierarchy",
            example = "ORG_ADMIN",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String parentRoleId,
    @Schema(description = "When the role was created", example = "2024-01-15T10:30:00Z")
        Instant createdAt) {
  /**
   * Creates a RoleResponse from a Role entity.
   *
   * @param role the role entity
   * @return the response DTO
   */
  public static RoleResponse from(Role role) {
    return new RoleResponse(
        role.getId(),
        role.getName(),
        role.getDescription(),
        role.getParentRoleId(),
        role.getCreatedAt());
  }
}
