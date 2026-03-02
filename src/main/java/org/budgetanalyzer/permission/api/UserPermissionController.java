package org.budgetanalyzer.permission.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.budgetanalyzer.permission.api.request.UserRoleAssignmentRequest;
import org.budgetanalyzer.permission.api.response.RoleResponse;
import org.budgetanalyzer.permission.api.response.UserPermissionsResponse;
import org.budgetanalyzer.permission.service.PermissionService;
import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.security.SecurityContextUtil;

/**
 * Controller for user permission management.
 *
 * <p>Handles querying user permissions, roles, and role assignments.
 */
@Tag(name = "User Permissions", description = "User permission management")
@RestController
@RequestMapping("/v1/users")
public class UserPermissionController {

  private final PermissionService permissionService;

  public UserPermissionController(PermissionService permissionService) {
    this.permissionService = permissionService;
  }

  @Operation(
      summary = "Get current user's permissions",
      description = "Returns all effective permissions for the authenticated user")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Permissions retrieved successfully",
        content = @Content(schema = @Schema(implementation = UserPermissionsResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @GetMapping("/me/permissions")
  @PreAuthorize("isAuthenticated()")
  public UserPermissionsResponse getCurrentUserPermissions() {
    var userId =
        SecurityContextUtil.getCurrentUserId()
            .orElseThrow(() -> new IllegalStateException("User ID not found in security context"));
    return UserPermissionsResponse.from(permissionService.getEffectivePermissions(userId));
  }

  @Operation(
      summary = "Get user's permissions",
      description = "Returns all effective permissions for a specified user.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Permissions retrieved successfully",
        content = @Content(schema = @Schema(implementation = UserPermissionsResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "User not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @GetMapping("/{id}/permissions")
  @PreAuthorize("hasAuthority('users:read')")
  public UserPermissionsResponse getUserPermissions(
      @Parameter(description = "User ID", example = "usr_abc123") @PathVariable String id) {
    return UserPermissionsResponse.from(permissionService.getEffectivePermissions(id));
  }

  @Operation(summary = "Get user's roles", description = "Returns all roles assigned to a user.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Roles retrieved successfully",
        content = @Content(schema = @Schema(implementation = RoleResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @GetMapping("/{id}/roles")
  @PreAuthorize("hasAuthority('users:read') or #id == authentication.name")
  public List<RoleResponse> getUserRoles(
      @Parameter(description = "User ID", example = "usr_abc123") @PathVariable String id) {
    return permissionService.getUserRoles(id).stream().map(RoleResponse::from).toList();
  }

  @Operation(
      summary = "Assign role to user",
      description = "Assigns a role to a user. Requires 'roles:write' permission.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Role assigned successfully"),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "User or role not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "422",
        description = "User already has this role",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @PostMapping("/{id}/roles")
  @PreAuthorize("hasAuthority('roles:write')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void assignRole(
      @Parameter(description = "User ID", example = "usr_abc123") @PathVariable String id,
      @RequestBody @Valid UserRoleAssignmentRequest request) {
    var assignedBy =
        SecurityContextUtil.getCurrentUserId()
            .orElseThrow(() -> new IllegalStateException("User ID not found in security context"));
    permissionService.assignRole(id, request.roleId(), assignedBy);
  }

  @Operation(
      summary = "Revoke role from user",
      description = "Revokes a role from a user. Requires 'roles:write' permission.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Role revoked successfully"),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Role assignment not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @DeleteMapping("/{id}/roles/{roleId}")
  @PreAuthorize("hasAuthority('roles:write')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeRole(
      @Parameter(description = "User ID", example = "usr_abc123") @PathVariable String id,
      @Parameter(description = "Role ID to revoke", example = "USER") @PathVariable String roleId) {
    permissionService.revokeRole(id, roleId);
  }
}
