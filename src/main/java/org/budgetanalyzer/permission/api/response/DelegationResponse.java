package org.budgetanalyzer.permission.api.response;

import java.time.Instant;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.domain.Delegation;

/** Response DTO for a single delegation. */
public record DelegationResponse(
    @Schema(description = "Delegation ID", example = "123") Long id,
    @Schema(description = "User who created the delegation", example = "usr_abc123")
        String delegatorId,
    @Schema(description = "User who received the delegation", example = "usr_def456")
        String delegateeId,
    @Schema(description = "Delegation scope", example = "read_only") String scope,
    @Schema(
            description = "Type of resource being delegated",
            example = "account",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String resourceType,
    @Schema(
            description = "Specific resource IDs if not delegating all",
            example = "[\"acc_123\", \"acc_456\"]",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String[] resourceIds,
    @Schema(description = "When delegation becomes active", example = "2024-01-15T10:30:00Z")
        Instant validFrom,
    @Schema(
            description = "When delegation expires",
            example = "2024-12-31T23:59:59Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant validUntil,
    @Schema(
            description = "When delegation was revoked",
            example = "2024-06-15T14:00:00Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant revokedAt) {
  /**
   * Creates a DelegationResponse from a Delegation entity.
   *
   * @param delegation the delegation entity
   * @return the response DTO
   */
  public static DelegationResponse from(Delegation delegation) {
    return new DelegationResponse(
        delegation.getId(),
        delegation.getDelegatorId(),
        delegation.getDelegateeId(),
        delegation.getScope(),
        delegation.getResourceType(),
        delegation.getResourceIds(),
        delegation.getValidFrom(),
        delegation.getValidUntil(),
        delegation.getRevokedAt());
  }
}
