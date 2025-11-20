package org.budgetanalyzer.permission.api.response;

import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.service.dto.EffectivePermissions;

/** Response DTO for a user's effective permissions. */
public record UserPermissionsResponse(
    @Schema(
            description = "Set of all effective permission IDs from roles",
            example = "[\"transactions:read\", \"accounts:write\"]")
        Set<String> permissions,
    @Schema(description = "Resource-specific permissions granted to user")
        List<ResourcePermissionResponse> resourcePermissions,
    @Schema(description = "Active delegations granting additional access")
        List<DelegationResponse> delegations) {
  /**
   * Creates a UserPermissionsResponse from EffectivePermissions.
   *
   * @param effective the effective permissions from service layer
   * @return the response DTO
   */
  public static UserPermissionsResponse from(EffectivePermissions effective) {
    return new UserPermissionsResponse(
        effective.getAllPermissionIds(),
        effective.resourcePermissions().stream().map(ResourcePermissionResponse::from).toList(),
        effective.delegations().stream().map(DelegationResponse::from).toList());
  }
}
