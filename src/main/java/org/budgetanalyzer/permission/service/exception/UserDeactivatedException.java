package org.budgetanalyzer.permission.service.exception;

import org.budgetanalyzer.service.exception.BusinessException;

/**
 * Thrown when a deactivated user attempts to log in.
 *
 * <p>Results in HTTP 422 Unprocessable Entity.
 */
public class UserDeactivatedException extends BusinessException {

  /**
   * Constructs a new UserDeactivatedException.
   *
   * @param idpSub the identity provider subject identifier of the deactivated user
   */
  public UserDeactivatedException(String idpSub) {
    super("User with idpSub " + idpSub + " is deactivated", "USER_DEACTIVATED");
  }
}
