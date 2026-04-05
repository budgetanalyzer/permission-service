package org.budgetanalyzer.permission.api;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.budgetanalyzer.permission.api.response.InternalPermissionsResponse;
import org.budgetanalyzer.permission.service.PermissionService;
import org.budgetanalyzer.permission.service.UserSyncService;

/**
 * Internal controller for gateway permission lookups.
 *
 * <p>Called by the session-gateway to fetch user permissions for JWT minting. Syncs the user on
 * first login if they don't exist yet.
 */
@Tag(name = "Internal", description = "Internal endpoints for gateway integration")
@RestController
@RequestMapping("/internal/v1/users")
public class InternalPermissionController {

  private final UserSyncService userSyncService;
  private final PermissionService permissionService;

  public InternalPermissionController(
      UserSyncService userSyncService, PermissionService permissionService) {
    this.userSyncService = userSyncService;
    this.permissionService = permissionService;
  }

  @Operation(
      summary = "Get user permissions by identity provider subject",
      description =
          "Syncs the user from identity provider data and returns their effective permissions. "
              + "Creates the user with default role on first login.")
  @GetMapping("/{idpSub}/permissions")
  public InternalPermissionsResponse getUserPermissions(
      @Parameter(
              description =
                  "Identity provider subject identifier (OIDC sub claim, provider-agnostic)",
              example = "auth0|abc123")
          @PathVariable
          String idpSub,
      @Parameter(description = "User email") @RequestParam String email,
      @Parameter(description = "User display name") @RequestParam String displayName) {
    var user = userSyncService.syncUser(idpSub, email, displayName);
    var effective = permissionService.getEffectivePermissions(user.getId());
    return new InternalPermissionsResponse(
        user.getId(), effective.roles(), effective.permissions());
  }
}
