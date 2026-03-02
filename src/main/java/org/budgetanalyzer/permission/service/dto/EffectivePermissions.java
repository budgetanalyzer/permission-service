package org.budgetanalyzer.permission.service.dto;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

/** Contains a user's effective permissions from role assignments. */
@Schema(description = "Internal DTO containing all effective permissions for a user")
public record EffectivePermissions(
    @Schema(description = "Role IDs assigned to the user") Set<String> roles,
    @Schema(description = "Permission IDs from assigned roles") Set<String> permissions) {}
