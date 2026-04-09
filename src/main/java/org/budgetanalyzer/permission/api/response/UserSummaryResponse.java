package org.budgetanalyzer.permission.api.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.domain.UserStatus;

/** Summary response body for user list views. */
@Schema(description = "Summary representation of a user for list views")
public record UserSummaryResponse(
    @Schema(description = "Internal user ID", example = "usr_abc123") String id,
    @Schema(
            description = "Identity provider subject identifier",
            example = "auth0|67fd70c38eb9d43f1c93ea44")
        String idpSub,
    @Schema(description = "User email address", example = "admin@example.com") String email,
    @Schema(description = "Display name", example = "Admin User") String displayName,
    @Schema(description = "Current user status", example = "ACTIVE") UserStatus status,
    @Schema(description = "Assigned role IDs", example = "[\"ADMIN\"]") List<String> roleIds,
    @Schema(description = "Creation timestamp", example = "2026-04-01T12:00:00Z") Instant createdAt,
    @Schema(description = "Last update timestamp", example = "2026-04-08T12:00:00Z")
        Instant updatedAt,
    @Schema(description = "Deactivation timestamp", example = "2026-04-08T12:00:00Z")
        Instant deactivatedAt) {

  /**
   * Creates a summary response from a user and their assigned role IDs.
   *
   * @param user the user entity
   * @param roleIds the assigned role IDs
   * @return the summary response
   */
  public static UserSummaryResponse from(User user, List<String> roleIds) {
    return new UserSummaryResponse(
        user.getId(),
        user.getIdpSub(),
        user.getEmail(),
        user.getDisplayName(),
        user.getStatus(),
        List.copyOf(roleIds),
        user.getCreatedAt(),
        user.getUpdatedAt(),
        user.getDeactivatedAt());
  }
}
