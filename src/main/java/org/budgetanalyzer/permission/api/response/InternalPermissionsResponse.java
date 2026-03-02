package org.budgetanalyzer.permission.api.response;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response DTO for internal permission lookups (used by gateway for JWT minting). */
@Schema(description = "Internal permissions response for gateway JWT minting")
public record InternalPermissionsResponse(
    @Schema(description = "Internal user ID", example = "usr_abc123") String userId,
    @Schema(description = "Role IDs assigned to the user", example = "[\"USER\"]")
        Set<String> roles,
    @Schema(
            description = "Permission IDs from assigned roles",
            example = "[\"transactions:read\", \"accounts:write\"]")
        Set<String> permissions) {}
