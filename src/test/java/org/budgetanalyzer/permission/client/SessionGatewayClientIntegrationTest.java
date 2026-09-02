package org.budgetanalyzer.permission.client;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.stubbing.Scenario;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.config.SessionRevocationProperties;

@DisplayName("SessionGatewayClient")
class SessionGatewayClientIntegrationTest {

  private static final String REVOCATION_PATH =
      "/session-gateway/internal/v1/sessions/users/" + TestConstants.TEST_USER_ID;
  private static final String RETRY_SCENARIO = "retry session revocation";
  private static final String RETRY_SUCCEEDED = "retry succeeded";

  private WireMockServer wireMockServer;
  private SessionGatewayClient sessionGatewayClient;

  @BeforeEach
  void setUp() {
    wireMockServer = new WireMockServer(wireMockConfig().dynamicPort());
    wireMockServer.start();
    sessionGatewayClient = createSessionGatewayClient(wireMockServer.baseUrl());
  }

  @AfterEach
  void stopWireMockServer() {
    if (wireMockServer.isRunning()) {
      wireMockServer.stop();
    }
  }

  @Nested
  @DisplayName("revokeUserSessions")
  class RevokeUserSessionsTests {

    @Test
    void shouldSendDeleteRequestAndReturnRevokedOnSuccess() {
      wireMockServer.stubFor(delete(urlEqualTo(REVOCATION_PATH)).willReturn(noContent()));

      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      assertThat(result.revoked()).isTrue();
      assertThat(result.retryExhausted()).isFalse();
      wireMockServer.verify(1, deleteRequestedFor(urlEqualTo(REVOCATION_PATH)));
    }

    @Test
    void shouldReturnRevokedAfterTransientConnectionFailure() {
      wireMockServer.stubFor(
          delete(urlEqualTo(REVOCATION_PATH))
              .inScenario(RETRY_SCENARIO)
              .whenScenarioStateIs(Scenario.STARTED)
              .willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER))
              .willSetStateTo(RETRY_SUCCEEDED));
      wireMockServer.stubFor(
          delete(urlEqualTo(REVOCATION_PATH))
              .inScenario(RETRY_SCENARIO)
              .whenScenarioStateIs(RETRY_SUCCEEDED)
              .willReturn(noContent()));

      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      assertThat(result.revoked()).isTrue();
      assertThat(result.retryExhausted()).isFalse();
      wireMockServer.verify(2, deleteRequestedFor(urlEqualTo(REVOCATION_PATH)));
    }

    @ParameterizedTest
    @ValueSource(ints = {429, 500})
    void shouldReturnRevokedAfterRetryableHttpStatus(int retryableStatus) {
      wireMockServer.stubFor(
          delete(urlEqualTo(REVOCATION_PATH))
              .inScenario(RETRY_SCENARIO)
              .whenScenarioStateIs(Scenario.STARTED)
              .willReturn(aResponse().withStatus(retryableStatus))
              .willSetStateTo(RETRY_SUCCEEDED));
      wireMockServer.stubFor(
          delete(urlEqualTo(REVOCATION_PATH))
              .inScenario(RETRY_SCENARIO)
              .whenScenarioStateIs(RETRY_SUCCEEDED)
              .willReturn(noContent()));

      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      assertThat(result.revoked()).isTrue();
      assertThat(result.retryExhausted()).isFalse();
      wireMockServer.verify(2, deleteRequestedFor(urlEqualTo(REVOCATION_PATH)));
    }

    @Test
    void shouldNotRetryNonRetryableClientError() {
      wireMockServer.stubFor(
          delete(urlEqualTo(REVOCATION_PATH)).willReturn(aResponse().withStatus(400)));

      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      assertThat(result.revoked()).isFalse();
      assertThat(result.retryExhausted()).isFalse();
      wireMockServer.verify(1, deleteRequestedFor(urlEqualTo(REVOCATION_PATH)));
    }

    @Test
    void shouldReturnRetryExhaustedOnPersistentServerError() {
      wireMockServer.stubFor(
          delete(urlEqualTo(REVOCATION_PATH)).willReturn(aResponse().withStatus(503)));

      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      assertThat(result.revoked()).isFalse();
      assertThat(result.retryExhausted()).isTrue();
      wireMockServer.verify(3, deleteRequestedFor(urlEqualTo(REVOCATION_PATH)));
    }

    @Test
    void shouldReturnRetryExhaustedOnPersistentConnectionFailure() {
      wireMockServer.stop();

      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      assertThat(result.revoked()).isFalse();
      assertThat(result.retryExhausted()).isTrue();
    }
  }

  private SessionGatewayClient createSessionGatewayClient(String baseUrl) {
    var restClient = RestClient.builder().baseUrl(baseUrl + "/session-gateway").build();
    var sessionRevocationProperties =
        new SessionRevocationProperties(3, Duration.ZERO, 1.0, Duration.ZERO);
    return new SessionGatewayClient(restClient, sessionRevocationProperties);
  }
}
