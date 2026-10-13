package org.budgetanalyzer.permission.api.response;

import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.domain.UserStatus;
import org.budgetanalyzer.permission.service.dto.UserAuthorizationView;

/** Response for current internal user authorization lookups. */
@Schema(description = "Current account status and authorization grants for an internal user")
public record InternalAuthorizationResponse(
    @Schema(description = "Internal user ID", example = "usr_abc123") String userId,
    @Schema(description = "Current account status", example = "ACTIVE") UserStatus status,
    @Schema(description = "Role IDs assigned to an active user", example = "[\"USER\"]")
        Set<String> roles,
    @Schema(
            description = "Permission IDs resolved from an active user's assigned roles",
            example = "[\"transactions:read\", \"accounts:write\"]")
        Set<String> permissions) {

  /**
   * Creates an API response from the service authorization view.
   *
   * @param userAuthorizationView the current authorization view
   * @return the API response
   */
  public static InternalAuthorizationResponse from(UserAuthorizationView userAuthorizationView) {
    return new InternalAuthorizationResponse(
        userAuthorizationView.userId(),
        userAuthorizationView.status(),
        userAuthorizationView.roles(),
        userAuthorizationView.permissions());
  }
}
