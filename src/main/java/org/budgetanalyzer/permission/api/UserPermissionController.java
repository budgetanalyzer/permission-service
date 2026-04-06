package org.budgetanalyzer.permission.api;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.budgetanalyzer.permission.api.response.UserDeactivationResponse;
import org.budgetanalyzer.permission.service.UserService;
import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.security.SecurityContextUtil;

/** Controller for user administration. */
@Tag(name = "User Administration", description = "User administration operations")
@RestController
@RequestMapping("/v1/users")
public class UserPermissionController {

  private final UserService userService;

  public UserPermissionController(UserService userService) {
    this.userService = userService;
  }

  @Operation(
      summary = "Deactivate a user",
      description =
          "Marks a user as deactivated, removes all role assignments, and revokes active sessions. "
              + "Idempotent — deactivating an already-deactivated user returns 200.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "User deactivated successfully",
        content = @Content(schema = @Schema(implementation = UserDeactivationResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "User not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "503",
        description = "Session revocation failed after retry; user was deactivated, retry is safe",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @PostMapping("/{id}/deactivate")
  @PreAuthorize("hasAuthority('users:write')")
  public UserDeactivationResponse deactivateUser(
      @Parameter(description = "User ID", example = "usr_abc123") @PathVariable String id) {
    var deactivatedBy =
        SecurityContextUtil.getCurrentUserId()
            .orElseThrow(() -> new IllegalStateException("User ID not found in security context"));
    var result = userService.deactivateUser(id, deactivatedBy);
    return new UserDeactivationResponse(
        result.userId(), result.status(), result.rolesRemoved(), result.sessionsRevoked());
  }
}
