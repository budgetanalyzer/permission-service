package org.budgetanalyzer.permission.api;

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

import org.budgetanalyzer.permission.api.request.DelegationRequest;
import org.budgetanalyzer.permission.api.response.DelegationResponse;
import org.budgetanalyzer.permission.api.response.DelegationsResponse;
import org.budgetanalyzer.permission.service.DelegationService;
import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.security.SecurityContextUtil;

/**
 * Controller for delegation management.
 *
 * <p>Handles user-to-user permission delegations for shared access.
 */
@Tag(name = "Delegations", description = "User-to-user permission delegations for shared access")
@RestController
@RequestMapping("/v1/delegations")
@PreAuthorize("isAuthenticated()")
public class DelegationController {

  private final DelegationService delegationService;

  /**
   * Constructs a new DelegationController.
   *
   * @param delegationService the delegation service
   */
  public DelegationController(DelegationService delegationService) {
    this.delegationService = delegationService;
  }

  @Operation(
      summary = "Get user's delegations",
      description = "Returns all delegations given by and received by the authenticated user")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Delegations retrieved successfully",
        content = @Content(schema = @Schema(implementation = DelegationsResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "Not authenticated",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @GetMapping
  public DelegationsResponse getDelegations() {
    var userId =
        SecurityContextUtil.getCurrentUserId()
            .orElseThrow(() -> new IllegalStateException("User ID not found in security context"));
    var summary = delegationService.getDelegationsForUser(userId);

    return DelegationsResponse.from(summary);
  }

  @Operation(
      summary = "Create delegation",
      description =
          "Creates a new delegation to share access with another user. "
              + "The authenticated user becomes the delegator.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Delegation created successfully",
        content = @Content(schema = @Schema(implementation = DelegationResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Invalid request data",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Delegatee not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @PostMapping
  public ResponseEntity<DelegationResponse> createDelegation(
      @RequestBody @Valid DelegationRequest request) {
    var delegatorId =
        SecurityContextUtil.getCurrentUserId()
            .orElseThrow(() -> new IllegalStateException("User ID not found in security context"));
    var created =
        delegationService.createDelegation(
            delegatorId,
            request.delegateeId(),
            request.scope(),
            request.resourceType(),
            request.resourceIds(),
            request.validUntil());

    var location =
        ServletUriComponentsBuilder.fromCurrentRequest()
            .path("/{id}")
            .buildAndExpand(created.getId())
            .toUri();

    return ResponseEntity.created(location).body(DelegationResponse.from(created));
  }

  @Operation(
      summary = "Revoke delegation",
      description = "Revokes a delegation. Only the delegator can revoke their own delegations.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Delegation revoked successfully"),
    @ApiResponse(
        responseCode = "403",
        description = "Not authorized to revoke this delegation",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Delegation not found",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void revokeDelegation(
      @Parameter(description = "Delegation ID", example = "123") @PathVariable Long id) {
    var revokedBy =
        SecurityContextUtil.getCurrentUserId()
            .orElseThrow(() -> new IllegalStateException("User ID not found in security context"));
    delegationService.revokeDelegation(id, revokedBy);
  }
}
