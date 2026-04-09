package org.budgetanalyzer.permission.api;

import java.util.List;

import jakarta.validation.Valid;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
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

import org.budgetanalyzer.permission.api.request.UserFilter;
import org.budgetanalyzer.permission.api.response.UserDeactivationResponse;
import org.budgetanalyzer.permission.api.response.UserDetailResponse;
import org.budgetanalyzer.permission.api.response.UserSummaryResponse;
import org.budgetanalyzer.permission.service.UserService;
import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.api.PagedResponse;
import org.budgetanalyzer.service.exception.InvalidRequestException;
import org.budgetanalyzer.service.security.SecurityContextUtil;

/** Controller for user administration. */
@Tag(name = "User Administration", description = "User administration operations")
@RestController
@RequestMapping("/v1/users")
public class UserController {

  private static final List<String> ALLOWED_SORT_FIELDS =
      List.of("id", "email", "displayName", "status", "createdAt", "updatedAt", "deactivatedAt");

  private final UserService userService;

  public UserController(UserService userService) {
    this.userService = userService;
  }

  @Operation(
      summary = "Search users",
      description = "Lists users with filtering, sorting, and pagination.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Users retrieved successfully",
        useReturnTypeSchema = true),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @GetMapping(produces = "application/json")
  @PreAuthorize("hasAuthority('users:read')")
  public PagedResponse<UserSummaryResponse> getUsers(
      @ParameterObject @Valid UserFilter userFilter,
      @ParameterObject
          @PageableDefault(
              size = 50,
              sort = {"createdAt", "id"},
              direction = Sort.Direction.DESC)
          Pageable pageable) {
    validateSortFields(pageable);
    var userPage =
        userService.search(userFilter == null ? UserFilter.empty() : userFilter, pageable);
    return PagedResponse.from(
        userPage,
        userWithRoles -> UserSummaryResponse.from(userWithRoles.user(), userWithRoles.roleIds()));
  }

  @Operation(
      summary = "Get user",
      description = "Returns a single user's details and assigned roles.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "User retrieved successfully",
        content = @Content(schema = @Schema(implementation = UserDetailResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "User not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @GetMapping(path = "/{id}", produces = "application/json")
  @PreAuthorize("hasAuthority('users:read')")
  public UserDetailResponse getUser(
      @Parameter(description = "User ID", example = "usr_abc123") @PathVariable String id) {
    var userWithRoles = userService.getUserWithRoles(id);
    return UserDetailResponse.from(userWithRoles.user(), userWithRoles.roleIds());
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
  @PostMapping(path = "/{id}/deactivate", produces = "application/json")
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

  private void validateSortFields(Pageable pageable) {
    for (var sortOrder : pageable.getSort()) {
      if (!ALLOWED_SORT_FIELDS.contains(sortOrder.getProperty())) {
        throw new InvalidRequestException(
            "Unsupported sort field: "
                + sortOrder.getProperty()
                + ". Allowed sort fields: "
                + String.join(", ", ALLOWED_SORT_FIELDS));
      }
    }
  }
}
