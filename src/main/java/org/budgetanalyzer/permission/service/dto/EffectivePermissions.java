package org.budgetanalyzer.permission.service.dto;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.domain.Delegation;
import org.budgetanalyzer.permission.domain.ResourcePermission;

/**
 * Contains a user's effective permissions from all sources.
 *
 * <p>Used by PermissionService, transformed to UserPermissionsResponse by controller.
 */
@Schema(description = "Internal DTO containing all effective permissions for a user")
public record EffectivePermissions(
    @Schema(description = "Permission IDs from assigned roles") Set<String> rolePermissions,
    @Schema(description = "Resource-specific permissions")
        List<ResourcePermission> resourcePermissions,
    @Schema(description = "Active delegations") List<Delegation> delegations) {
  /**
   * Combines all permission sources into a single set of permission IDs.
   *
   * @return set of all effective permission IDs
   */
  public Set<String> getAllPermissionIds() {
    var all = new HashSet<>(rolePermissions);
    resourcePermissions.forEach(rp -> all.add(rp.getPermission()));
    return all;
  }
}
