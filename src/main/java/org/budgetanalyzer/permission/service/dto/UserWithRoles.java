package org.budgetanalyzer.permission.service.dto;

import java.util.List;

import org.budgetanalyzer.permission.domain.User;

/** A user paired with the IDs of the roles assigned to it. */
public record UserWithRoles(User user, List<String> roleIds) {

  /** Defensively copies {@code roleIds} to keep the record immutable. */
  public UserWithRoles {
    roleIds = List.copyOf(roleIds);
  }
}
