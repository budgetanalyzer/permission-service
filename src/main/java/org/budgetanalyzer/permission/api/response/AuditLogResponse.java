package org.budgetanalyzer.permission.api.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.domain.AuthorizationAuditLog;

/** Response DTO for an audit log entry. */
public record AuditLogResponse(
    @Schema(description = "Audit log entry ID", example = "789") Long id,
    @Schema(description = "When the event occurred", example = "2024-01-15T10:30:00Z")
        Instant timestamp,
    @Schema(
            description = "User who performed the action",
            example = "usr_abc123",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String userId,
    @Schema(description = "Action performed", example = "ROLE_ASSIGNED") String action,
    @Schema(
            description = "Type of resource affected",
            example = "user-role",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String resourceType,
    @Schema(
            description = "ID of resource affected",
            example = "usr_def456",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String resourceId,
    @Schema(description = "Access decision", example = "GRANTED") String decision,
    @Schema(
            description = "Reason for decision",
            example = "User has required permission",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String reason) {
  /**
   * Creates an AuditLogResponse from an AuthorizationAuditLog entity.
   *
   * @param log the audit log entity
   * @return the response DTO
   */
  public static AuditLogResponse from(AuthorizationAuditLog log) {
    return new AuditLogResponse(
        log.getId(),
        log.getTimestamp(),
        log.getUserId(),
        log.getAction(),
        log.getResourceType(),
        log.getResourceId(),
        log.getDecision(),
        log.getReason());
  }
}
