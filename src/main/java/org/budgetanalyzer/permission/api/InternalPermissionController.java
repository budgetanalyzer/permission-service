package org.budgetanalyzer.permission.api;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.budgetanalyzer.permission.api.request.UserSyncRequest;
import org.budgetanalyzer.permission.api.response.InternalAuthorizationResponse;
import org.budgetanalyzer.permission.api.response.InternalPermissionsResponse;
import org.budgetanalyzer.permission.service.PermissionService;
import org.budgetanalyzer.permission.service.UserSyncService;
import org.budgetanalyzer.service.api.ApiErrorResponse;

/**
 * Internal controller for gateway permission lookups.
 *
 * <p>Session Gateway calls the login-time user sync POST, which creates the user on first login and
 * keeps all identity provider fields in the request body, and calls the read-only authorization
 * lookup on each heartbeat refresh.
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
      summary = "Sync user and get permissions",
      description =
          "Syncs the user from identity provider data in the request body and returns their "
              + "effective permissions. Creates the user with default role on first login. "
              + "Deactivated users are rejected without updating their profile.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "User synced and permissions retrieved successfully",
        content = @Content(schema = @Schema(implementation = InternalPermissionsResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description = "User is deactivated",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @PostMapping(path = "/sync", consumes = "application/json", produces = "application/json")
  public InternalPermissionsResponse syncUser(@Valid @RequestBody UserSyncRequest userSyncRequest) {
    var user =
        userSyncService.syncUser(
            userSyncRequest.idpSub(), userSyncRequest.email(), userSyncRequest.displayName());
    var effective = permissionService.getEffectivePermissions(user.getId());
    return new InternalPermissionsResponse(
        user.getId(), effective.roles(), effective.permissions());
  }

  @Operation(
      summary = "Get current user authorization by internal user ID",
      description =
          "Returns the current account status and effective grants without creating or updating "
              + "the user. Deactivated users return empty role and permission collections.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Current authorization retrieved successfully",
        content = @Content(schema = @Schema(implementation = InternalAuthorizationResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "User not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @GetMapping(path = "/{userId}/authorization", produces = "application/json")
  public InternalAuthorizationResponse getUserAuthorization(
      @Parameter(description = "Internal user ID", example = "usr_abc123") @PathVariable
          String userId) {
    return InternalAuthorizationResponse.from(permissionService.getUserAuthorization(userId));
  }
}
