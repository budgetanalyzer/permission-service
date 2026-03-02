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
import org.budgetanalyzer.service.api.ApiErrorResponse;
import org.budgetanalyzer.service.api.ApiErrorType;

/**
 * Exception handler for Spring Security exceptions.
 *
 * <p>Handles authentication and authorization exceptions, converting them to standardized {@link
 * ApiErrorResponse} objects.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SecurityExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(SecurityExceptionHandler.class);

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
