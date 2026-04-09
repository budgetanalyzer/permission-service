package org.budgetanalyzer.permission.api.response;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.domain.UserStatus;
import org.budgetanalyzer.permission.service.dto.UserDetail;

/** Detailed response body for user administration views. */
@Schema(description = "Detailed representation of a user")
public record UserDetailResponse(
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
        Instant deactivatedAt,
    @Schema(description = "User that deactivated the user") UserReference deactivatedBy,
    @Schema(description = "Soft-delete timestamp", example = "2026-04-09T12:00:00Z")
        Instant deletedAt,
    @Schema(description = "User that soft-deleted the user") UserReference deletedBy) {

  /**
   * Creates a detail response from a resolved user detail projection.
   *
   * @param userDetail the resolved user detail
   * @return the detail response
   */
  public static UserDetailResponse from(UserDetail userDetail) {
    var user = userDetail.user();
    return new UserDetailResponse(
        user.getId(),
        user.getIdpSub(),
        user.getEmail(),
        user.getDisplayName(),
        user.getStatus(),
        List.copyOf(userDetail.roleIds()),
        user.getCreatedAt(),
        user.getUpdatedAt(),
        user.getDeactivatedAt(),
        userDetail.deactivatedBy(),
        user.getDeletedAt(),
        userDetail.deletedBy());
  }
}
