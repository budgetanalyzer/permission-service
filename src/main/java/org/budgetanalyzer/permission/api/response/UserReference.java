package org.budgetanalyzer.permission.api.response;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.service.dto.UserActor;

/** Compact reference to a user for audit fields on detail responses. */
@Schema(description = "Dereferenced user identity for audit fields")
public record UserReference(
    @Schema(description = "Internal user ID", example = "usr_admin456") String id,
    @Schema(description = "Display name", example = "Admin User") String displayName,
    @Schema(description = "User email address", example = "admin@example.com") String email) {

  /**
   * Creates a reference from a resolved user.
   *
   * @param user the resolved user
   * @return the user reference
   */
  public static UserReference from(User user) {
    return new UserReference(user.getId(), user.getDisplayName(), user.getEmail());
  }

  /**
   * Creates a reference from a service-layer actor.
   *
   * @param userActor the service-layer actor
   * @return the user reference
   */
  public static UserReference from(UserActor userActor) {
    return new UserReference(userActor.id(), userActor.displayName(), userActor.email());
  }

  /**
   * Creates a degraded reference when only the actor ID is known.
   *
   * @param id the unresolved actor ID
   * @return the degraded user reference
   */
  public static UserReference ofIdOnly(String id) {
    return new UserReference(id, null, null);
  }
}
