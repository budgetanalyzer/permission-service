package org.budgetanalyzer.permission.api.request;

import jakarta.validation.constraints.NotBlank;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request DTO for assigning a role to a user. */
public record UserRoleAssignmentRequest(
    @Schema(
            description = "Role ID to assign",
            example = "USER",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Role ID is required")
        String roleId) {}
