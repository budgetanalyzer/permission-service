package org.budgetanalyzer.permission.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.budgetanalyzer.permission.api.request.RoleRequest;
import org.budgetanalyzer.permission.api.response.RoleResponse;
import org.budgetanalyzer.permission.service.RoleService;
import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.security.SecurityContextUtil;

/**
 * Controller for role management.
 *
 * <p>Provides CRUD operations for authorization roles.
 */
@Tag(name = "Roles", description = "Role management - CRUD operations for authorization roles")
@RestController
@RequestMapping("/v1/roles")
public class RoleController {

  private final RoleService roleService;

  /**
   * Constructs a new RoleController.
   *
   * @param roleService the role service
   */
  public RoleController(RoleService roleService) {
    this.roleService = roleService;
  }

  @Operation(
      summary = "List all roles",
      description = "Returns all active (non-deleted) roles. Requires 'roles:read' permission.")
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
  @GetMapping
  @PreAuthorize("hasAuthority('roles:read')")
  public List<RoleResponse> getAllRoles() {
    return roleService.getAllRoles().stream().map(RoleResponse::from).toList();
  }

  @Operation(
      summary = "Get role by ID",
      description = "Returns a specific role by ID. Requires 'roles:read' permission.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Role retrieved successfully",
        content = @Content(schema = @Schema(implementation = RoleResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Role not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @GetMapping("/{id}")
  @PreAuthorize("hasAuthority('roles:read')")
  public RoleResponse getRole(
      @Parameter(description = "Role ID", example = "MANAGER") @PathVariable String id) {
    return RoleResponse.from(roleService.getRole(id));
  }

  @Operation(
      summary = "Create new role",
      description = "Creates a new role. Requires 'roles:write' permission (SYSTEM_ADMIN only).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Role created successfully",
        content = @Content(schema = @Schema(implementation = RoleResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request data",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @PostMapping
  @PreAuthorize("hasAuthority('roles:write')")
  public ResponseEntity<RoleResponse> createRole(@RequestBody @Valid RoleRequest request) {
    var created =
        roleService.createRole(request.name(), request.description(), request.parentRoleId());

    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();

    return ResponseEntity.created(location).body(RoleResponse.from(created));
  }

  @Operation(
      summary = "Update role",
      description =
          "Updates an existing role. Requires 'roles:write' permission (SYSTEM_ADMIN only).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Role updated successfully",
        content = @Content(schema = @Schema(implementation = RoleResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request data",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Role not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @PutMapping("/{id}")
  @PreAuthorize("hasAuthority('roles:write')")
  public RoleResponse updateRole(
      @Parameter(description = "Role ID", example = "MANAGER") @PathVariable String id,
      @RequestBody @Valid RoleRequest request) {
    return RoleResponse.from(
        roleService.updateRole(id, request.name(), request.description(), request.parentRoleId()));
  }

  @Operation(
      summary = "Delete role",
      description =
          "Soft-deletes a role and cascades revocation to all assignments. "
              + "Requires 'roles:delete' permission (SYSTEM_ADMIN only).")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Role deleted successfully"),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Role not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('roles:delete')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void deleteRole(
      @Parameter(description = "Role ID", example = "MANAGER") @PathVariable String id) {
    var deletedBy =
        SecurityContextUtil.getCurrentUserId()
            .orElseThrow(() -> new IllegalStateException("User ID not found in security context"));
    roleService.deleteRole(id, deletedBy);
  }
}
