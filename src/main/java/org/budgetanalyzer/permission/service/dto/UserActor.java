package org.budgetanalyzer.permission.service.dto;

import org.budgetanalyzer.permission.domain.User;

/** Service-layer projection of a user acting as an audit actor. */
public record UserActor(String id, String displayName, String email) {

  /**
   * Creates an actor from a resolved user.
   *
   * @param user the resolved user
   * @return the user actor
   */
  public static UserActor from(User user) {
    return new UserActor(user.getId(), user.getDisplayName(), user.getEmail());
  }

  /**
   * Creates a degraded actor when only the id is known.
   *
   * @param id the unresolved actor id
   * @return the degraded user actor
   */
  public static UserActor ofIdOnly(String id) {
    return new UserActor(id, null, null);
  }
}
