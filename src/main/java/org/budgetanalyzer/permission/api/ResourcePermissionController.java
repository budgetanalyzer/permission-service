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

import org.budgetanalyzer.permission.api.request.ResourcePermissionRequest;
import org.budgetanalyzer.permission.api.response.ResourcePermissionResponse;
import org.budgetanalyzer.permission.service.ResourcePermissionService;
import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.security.SecurityContextUtil;

/**
 * Controller for resource-specific permission management.
 *
 * <p>Handles fine-grained permissions for specific resource instances.
 */
@Tag(
    name = "Resource Permissions",
    description = "Fine-grained permissions for specific resource instances")
@RestController
@RequestMapping("/v1/resource-permissions")
public class ResourcePermissionController {

  private final ResourcePermissionService resourcePermissionService;

  /**
   * Constructs a new ResourcePermissionController.
   *
   * @param resourcePermissionService the resource permission service
   */
  public ResourcePermissionController(ResourcePermissionService resourcePermissionService) {
    this.resourcePermissionService = resourcePermissionService;
  }

  @Operation(
      summary = "Grant resource-specific permission",
      description =
          "Grants a permission for a specific resource instance to a user. "
              + "Requires admin role or ownership of the resource.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Permission granted successfully",
        content = @Content(schema = @Schema(implementation = ResourcePermissionResponse.class))),
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
        description = "User not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @PostMapping
  @PreAuthorize("hasAuthority('permissions:write')")
  public ResponseEntity<ResourcePermissionResponse> grantPermission(
      @RequestBody @Valid ResourcePermissionRequest request) {
    var grantedBy =
        SecurityContextUtil.getCurrentUserId()
            .orElseThrow(() -> new IllegalStateException("User ID not found in security context"));
    var created =
        resourcePermissionService.grantPermission(
            request.userId(),
            request.resourceType(),
            request.resourceId(),
            request.permission(),
            request.expiresAt(),
            request.reason(),
            grantedBy);

    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();

    return ResponseEntity.created(location).body(ResourcePermissionResponse.from(created));
  }

  @Operation(
      summary = "Revoke resource-specific permission",
      description = "Revokes a resource-specific permission. Requires admin permissions.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Permission revoked successfully"),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Resource permission not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('permissions:write')")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokePermission(
      @Parameter(description = "Resource permission ID", example = "456") @PathVariable Long id) {
    var revokedBy =
        SecurityContextUtil.getCurrentUserId()
            .orElseThrow(() -> new IllegalStateException("User ID not found in security context"));
    resourcePermissionService.revokePermission(id, revokedBy);
  }

  @Operation(
      summary = "Get resource permissions for user",
      description =
          "Returns all active resource-specific permissions for a user. "
              + "User can view their own or requires admin permissions.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Permissions retrieved successfully",
        content = @Content(schema = @Schema(implementation = ResourcePermissionResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @GetMapping("/user/{userId}")
  @PreAuthorize("hasAuthority('permissions:read') or #userId == authentication.name")
  public List<ResourcePermissionResponse> getUserResourcePermissions(
      @Parameter(description = "User ID", example = "usr_abc123") @PathVariable String userId) {
    return resourcePermissionService.getForUser(userId).stream()
        .map(ResourcePermissionResponse::from)
        .toList();
  }
}
