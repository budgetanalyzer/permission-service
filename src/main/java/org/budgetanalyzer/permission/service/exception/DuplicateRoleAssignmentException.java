package org.budgetanalyzer.permission.service.exception;

import org.budgetanalyzer.service.exception.BusinessException;

/**
 * Thrown when a user already has an active role assignment.
 *
 * <p>Results in HTTP 422 Unprocessable Entity.
 */
public class DuplicateRoleAssignmentException extends BusinessException {

  /**
   * Constructs a new DuplicateRoleAssignmentException.
   *
   * @param userId the user ID with the duplicate assignment
   * @param roleId the role ID that is already assigned
   */
  public DuplicateRoleAssignmentException(String userId, String roleId) {
    super("User " + userId + " already has role " + roleId, "DUPLICATE_ROLE_ASSIGNMENT");
  }
}
