package org.budgetanalyzer.permission.api.response;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

/** Response for the login-time internal permission lookup used by Session Gateway. */
@Schema(description = "Internal login-time permissions response for Session Gateway")
public record InternalPermissionsResponse(
    @Schema(description = "Internal user ID", example = "usr_abc123") String userId,
    @Schema(description = "Role IDs assigned to the user", example = "[\"USER\"]")
        Set<String> roles,
    @Schema(
            description = "Permission IDs from assigned roles",
            example = "[\"transactions:read\", \"accounts:write\"]")
        Set<String> permissions) {}
