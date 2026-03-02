package org.budgetanalyzer.permission.api.response;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.service.dto.EffectivePermissions;

/** Response DTO for a user's effective permissions. */
public record UserPermissionsResponse(
    @Schema(
            description = "Set of all effective permission IDs from roles",
            example = "[\"transactions:read\", \"accounts:write\"]")
        Set<String> permissions) {

  public static UserPermissionsResponse from(EffectivePermissions effective) {
    return new UserPermissionsResponse(effective.permissions());
  }
}
