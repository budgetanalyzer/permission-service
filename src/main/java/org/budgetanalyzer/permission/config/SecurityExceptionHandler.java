package org.budgetanalyzer.permission.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;

import org.budgetanalyzer.permission.service.exception.PermissionDeniedException;
import org.budgetanalyzer.permission.service.exception.ProtectedRoleException;
import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.api.ApiErrorType;

/**
 * Exception handler for Spring Security exceptions.
 *
 * <p>Handles authentication and authorization exceptions, converting them to standardized {@link
 * ApiErrorResponse} objects. This handler has highest precedence to ensure security exceptions are
 * properly handled before falling through to the generic handler.
 *
 * <p>Exception to HTTP status mapping:
 *
 * <ul>
 *   <li>{@link AuthenticationException} → 401 Unauthorized
 *   <li>{@link AccessDeniedException} → 403 Forbidden
 *   <li>{@link AuthorizationDeniedException} → 403 Forbidden
 * </ul>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);

  /**
   * Handles {@link AuthenticationException} and returns HTTP 401 Unauthorized.
   *
   * @param exception the authentication exception
   * @param request the web request context
   * @return standardized error response with UNAUTHORIZED type
   */
  @ExceptionHandler(AuthenticationException.class)
  @ResponseStatus(HttpStatus.UNAUTHORIZED)
  public ApiErrorResponse handleAuthenticationException(
      AuthenticationException exception, WebRequest request) {
    log.warn(
        "Authentication failed: {} message: {}",
        exception.getClass().getSimpleName(),
        exception.getMessage());
    return ApiErrorResponse.builder()
        .type(ApiErrorType.UNAUTHORIZED)
        .message("Authentication required")
        .build();
  }

  /**
   * Handles {@link AccessDeniedException} and returns HTTP 403 Forbidden.
   *
   * @param exception the access denied exception
   * @param request the web request context
   * @return standardized error response with PERMISSION_DENIED type
   */
  @ExceptionHandler(AccessDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiErrorResponse handleAccessDeniedException(
      AccessDeniedException exception, WebRequest request) {
    log.warn(
        "Access denied: {} message: {}",
        exception.getClass().getSimpleName(),
        exception.getMessage());
    return ApiErrorResponse.builder()
        .type(ApiErrorType.PERMISSION_DENIED)
        .message("You do not have permission to perform this action")
        .build();
  }

  /**
   * Handles {@link AuthorizationDeniedException} and returns HTTP 403 Forbidden.
   *
   * <p>This exception is thrown by Spring Security 6.x when authorization fails.
   *
   * @param exception the authorization denied exception
   * @param request the web request context
   * @return standardized error response with PERMISSION_DENIED type
   */
  @ExceptionHandler(AuthorizationDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiErrorResponse handleAuthorizationDeniedException(
      AuthorizationDeniedException exception, WebRequest request) {
    log.warn(
        "Authorization denied: {} message: {}",
        exception.getClass().getSimpleName(),
        exception.getMessage());
    return ApiErrorResponse.builder()
        .type(ApiErrorType.PERMISSION_DENIED)
        .message("You do not have permission to perform this action")
        .build();
  }

  /**
   * Handles {@link ProtectedRoleException} and returns HTTP 403 Forbidden.
   *
   * <p>This exception is thrown when trying to assign/revoke protected roles via API.
   *
   * @param exception the protected role exception
   * @param request the web request context
   * @return standardized error response with PERMISSION_DENIED type
   */
  @ExceptionHandler(ProtectedRoleException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiErrorResponse handleProtectedRoleException(
      ProtectedRoleException exception, WebRequest request) {
    log.warn(
        "Protected role violation: {} message: {}",
        exception.getClass().getSimpleName(),
        exception.getMessage());
    return ApiErrorResponse.builder()
        .type(ApiErrorType.PERMISSION_DENIED)
        .message(exception.getMessage())
        .build();
  }

  /**
   * Handles {@link PermissionDeniedException} and returns HTTP 403 Forbidden.
   *
   * <p>This exception is thrown when a user lacks the required permission for an operation.
   *
   * @param exception the permission denied exception
   * @param request the web request context
   * @return standardized error response with PERMISSION_DENIED type
   */
  @ExceptionHandler(PermissionDeniedException.class)
  @ResponseStatus(HttpStatus.FORBIDDEN)
  public ApiErrorResponse handlePermissionDeniedException(
      PermissionDeniedException exception, WebRequest request) {
    log.warn(
        "Permission denied: {} message: {}",
        exception.getClass().getSimpleName(),
        exception.getMessage());
    return ApiErrorResponse.builder()
        .type(ApiErrorType.PERMISSION_DENIED)
        .message(exception.getMessage())
        .build();
  }
}
