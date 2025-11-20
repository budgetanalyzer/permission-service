package org.budgetanalyzer.permission.api.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request DTO for assigning a role to a user. */
public record UserRoleAssignmentRequest(
    @Schema(
            description = "Role ID to assign",
            example = "ACCOUNTANT",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Role ID is required")
        String roleId,
    @Schema(
            description = "Organization scope for multi-tenancy",
            example = "org_123",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String organizationId,
    @Schema(
            description = "Optional expiration for temporary assignments",
            example = "2024-12-31T23:59:59Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant expiresAt) {}
