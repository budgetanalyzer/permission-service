package org.budgetanalyzer.permission.service.exception;

import org.budgetanalyzer.service.exception.BusinessException;

/**
 * Thrown when a user attempts an operation they don't have permission for.
 *
 * <p>Results in HTTP 403 Forbidden.
 */
public class PermissionDeniedException extends BusinessException {

  /**
   * Constructs a new PermissionDeniedException with default error code.
   *
   * @param message the error message
   */
  public PermissionDeniedException(String message) {
    super(message, "PERMISSION_DENIED");
  }

  /**
   * Constructs a new PermissionDeniedException with custom error code.
   *
   * @param message the error message
   * @param errorCode the error code
   */
  public PermissionDeniedException(String message, String errorCode) {
    super(message, errorCode);
  }
}
