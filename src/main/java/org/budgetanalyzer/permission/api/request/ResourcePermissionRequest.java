package org.budgetanalyzer.permission.api.request;

import java.time.Instant;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request DTO for granting a permission on a specific resource. */
public record ResourcePermissionRequest(
    @Schema(
            description = "User ID to grant permission to",
            example = "usr_abc123",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "User ID is required")
        String userId,
    @Schema(
            description = "Type of resource",
            example = "account",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Resource type is required")
        String resourceType,
    @Schema(
            description = "Specific resource ID",
            example = "acc_789",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Resource ID is required")
        String resourceId,
    @Schema(
            description = "Permission to grant",
            example = "read",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Permission is required")
        String permission,
    @Schema(
            description = "When permission expires",
            example = "2024-12-31T23:59:59Z",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        Instant expiresAt,
    @Schema(
            description = "Reason for granting permission",
            example = "Audit review access",
            maxLength = 500,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 500, message = "Reason must be at most 500 characters")
        String reason) {}
