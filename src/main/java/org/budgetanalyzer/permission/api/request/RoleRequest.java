package org.budgetanalyzer.permission.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request DTO for creating or updating a role. */
public record RoleRequest(
    @Schema(
            description = "Role name",
            example = "Project Manager",
            maxLength = 100,
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank(message = "Role name is required")
        @Size(max = 100, message = "Role name must be at most 100 characters")
        String name,
    @Schema(
            description = "Role description",
            example = "Manages project resources and timelines",
            maxLength = 500,
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        @Size(max = 500, message = "Description must be at most 500 characters")
        String description,
    @Schema(
            description = "Parent role ID for hierarchy inheritance",
            example = "MANAGER",
            requiredMode = Schema.RequiredMode.NOT_REQUIRED)
        String parentRoleId) {}
