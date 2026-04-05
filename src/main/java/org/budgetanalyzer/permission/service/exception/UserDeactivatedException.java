package org.budgetanalyzer.permission.service.exception;

import org.budgetanalyzer.service.exception.BusinessException;

/**
 * Thrown when an operation is attempted on a deactivated user.
 *
 * <p>Results in HTTP 422 Unprocessable Entity.
 */
public class UserDeactivatedException extends BusinessException {

  /**
   * Constructs a new UserDeactivatedException.
   *
   * @param message the detail message describing the deactivation context
   */
  public UserDeactivatedException(String message) {
    super(message, "USER_DEACTIVATED");
  }
}
