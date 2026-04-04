package org.budgetanalyzer.permission.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Client for Session Gateway internal API.
 *
 * <p>Used to revoke active sessions when a user is deactivated. Failures are logged but never
 * propagated — the PostgreSQL status change is the durable gate.
 */
@Component
public class SessionGatewayClient {

  private static final Logger log = LoggerFactory.getLogger(SessionGatewayClient.class);

  private final RestClient sessionGatewayRestClient;

  /**
   * Constructs a SessionGatewayClient.
   *
   * @param sessionGatewayRestClient the RestClient configured for Session Gateway
   */
  public SessionGatewayClient(RestClient sessionGatewayRestClient) {
    this.sessionGatewayRestClient = sessionGatewayRestClient;
  }

  /**
   * Revokes all active sessions for a user.
   *
   * @param userId the user ID whose sessions should be revoked
   * @return true if sessions were revoked successfully, false on any failure
   */
  @SuppressWarnings("java:S1166") // Exception intentionally caught and logged without rethrowing
  public boolean revokeUserSessions(String userId) {
    try {
      sessionGatewayRestClient
          .delete()
          .uri("/internal/v1/sessions/users/{userId}", userId)
          .retrieve()
          .toBodilessEntity();
      return true;
    } catch (Exception exception) {
      log.error("Failed to revoke sessions for user {}: {}", userId, exception.getMessage());
      return false;
    }
  }
}
