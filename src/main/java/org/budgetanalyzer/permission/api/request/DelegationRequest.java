package org.budgetanalyzer.permission.api.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request DTO for creating a delegation. */
public record DelegationRequest(
    @Schema(
            description = "User ID to delegate to",
            example = "usr_def456",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Delegatee ID is required")
        String delegateeId,
    @Schema(
            description = "Delegation scope: full, read_only, transactions_only",
            example = "read_only",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Scope is required")
        String scope,
    @Schema(
            description = "Type of resource being delegated",
            example = "account",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String resourceType,
    @Schema(
            description = "Specific resource IDs (null = all resources of type)",
            example = "[\"acc_123\", \"acc_456\"]",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String[] resourceIds,
    @Schema(
            description = "When delegation expires",
            example = "2024-12-31T23:59:59Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant validUntil) {}
