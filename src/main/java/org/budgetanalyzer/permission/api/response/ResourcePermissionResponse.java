package org.budgetanalyzer.permission.api.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.domain.ResourcePermission;

/** Response DTO for a resource-specific permission. */
public record ResourcePermissionResponse(
    @Schema(description = "Resource permission ID", example = "456") Long id,
    @Schema(description = "User ID granted permission", example = "usr_abc123") String userId,
    @Schema(description = "Type of resource", example = "account") String resourceType,
    @Schema(description = "Specific resource ID", example = "acc_789") String resourceId,
    @Schema(description = "Permission granted", example = "read") String permission,
    @Schema(description = "When permission was granted", example = "2024-01-15T10:30:00Z")
        Instant grantedAt,
    @Schema(
            description = "Who granted the permission",
            example = "usr_admin",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String grantedBy,
    @Schema(
            description = "When permission expires",
            example = "2024-12-31T23:59:59Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant expiresAt,
    @Schema(
            description = "Reason for granting permission",
            example = "Temporary access for audit",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String reason) {
  /**
   * Creates a ResourcePermissionResponse from a ResourcePermission entity.
   *
   * @param rp the resource permission entity
   * @return the response DTO
   */
  public static ResourcePermissionResponse from(ResourcePermission rp) {
    return new ResourcePermissionResponse(
        rp.getId(),
        rp.getUserId(),
        rp.getResourceType(),
        rp.getResourceId(),
        rp.getPermission(),
        rp.getGrantedAt(),
        rp.getGrantedBy(),
        rp.getExpiresAt(),
        rp.getReason());
  }
}
