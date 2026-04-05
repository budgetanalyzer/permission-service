package org.budgetanalyzer.permission.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import org.budgetanalyzer.permission.config.SessionRevocationProperties;

/**
 * Client for Session Gateway internal API.
 *
 * <p>Revokes active sessions when a user is deactivated. Uses bounded retry with exponential
 * backoff for transient failures (connection errors, 5xx, 429). Non-retryable 4xx responses return
 * immediately.
 */
@Component
public class SessionGatewayClient {

  private static final Logger log = LoggerFactory.getLogger(SessionGatewayClient.class);

  private final RestClient sessionGatewayRestClient;
  private final SessionRevocationProperties revocationProperties;

  /**
   * Constructs a SessionGatewayClient.
   *
   * @param sessionGatewayRestClient the RestClient configured for Session Gateway
   * @param revocationProperties retry configuration for session revocation
   */
  public SessionGatewayClient(
      RestClient sessionGatewayRestClient, SessionRevocationProperties revocationProperties) {
    this.sessionGatewayRestClient = sessionGatewayRestClient;
    this.revocationProperties = revocationProperties;
  }

  /**
   * Revokes all active sessions for a user with bounded retry.
   *
   * <p>Retries on connection failures, 5xx responses, and 429 responses. Returns immediately for
   * other 4xx responses. The operation is idempotent — repeated calls are safe.
   *
   * @param userId the user ID whose sessions should be revoked
   * @return the revocation result indicating success, non-retryable failure, or retry exhaustion
   */
  @SuppressWarnings("java:S1166") // Exceptions intentionally caught for retry/logging
  public SessionRevocationResult revokeUserSessions(String userId) {
    var maxAttempts = revocationProperties.maxAttempts();
    var delayMs = revocationProperties.initialDelay().toMillis();

    for (var attempt = 1; attempt <= maxAttempts; attempt++) {
      try {
        sessionGatewayRestClient
            .delete()
            .uri("/internal/v1/sessions/users/{userId}", userId)
            .retrieve()
            .toBodilessEntity();
        return new SessionRevocationResult(true, false);
      } catch (RestClientResponseException exception) {
        if (!isRetryableStatus(exception.getStatusCode().value())) {
          log.error(
              "Non-retryable error revoking sessions for user {}: HTTP {}",
              userId,
              exception.getStatusCode().value());
          return new SessionRevocationResult(false, false);
        }
        logRetryableFailure(userId, attempt, maxAttempts, exception);
      } catch (Exception exception) {
        logRetryableFailure(userId, attempt, maxAttempts, exception);
      }

      if (attempt < maxAttempts) {
        sleep(delayMs);
        delayMs =
            Math.min(
                (long) (delayMs * revocationProperties.multiplier()),
                revocationProperties.maxDelay().toMillis());
      }
    }

    log.error(
        "Retry exhausted: failed to revoke sessions for user {} after {} attempts",
        userId,
        maxAttempts);
    return new SessionRevocationResult(false, true);
  }

  private boolean isRetryableStatus(int statusCode) {
    return statusCode >= 500 || statusCode == 429;
  }

  private void logRetryableFailure(
      String userId, int attempt, int maxAttempts, Exception exception) {
    log.warn(
        "Failed to revoke sessions for user {} (attempt {}/{}): {}",
        userId,
        attempt,
        maxAttempts,
        exception.getMessage());
  }

  @SuppressWarnings("java:S2142") // Interrupt flag is restored
  private void sleep(long millis) {
    try {
      Thread.sleep(millis);
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
    }
  }
}
