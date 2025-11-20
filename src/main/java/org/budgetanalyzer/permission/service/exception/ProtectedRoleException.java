package org.budgetanalyzer.permission.service.exception;

import org.budgetanalyzer.service.exception.BusinessException;

/**
 * Thrown when trying to assign/revoke protected roles via API.
 *
 * <p>Results in HTTP 403 Forbidden.
 */
public class ProtectedRoleException extends BusinessException {

  /**
   * Constructs a new ProtectedRoleException.
   *
   * @param message the error message
   */
  public ProtectedRoleException(String message) {
    super(message, "PROTECTED_ROLE_VIOLATION");
  }
}
