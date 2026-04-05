package org.budgetanalyzer.permission.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.config.SessionRevocationProperties;

@ExtendWith(MockitoExtension.class)
@DisplayName("SessionGatewayClient")
class SessionGatewayClientTest {

  @Mock private RestClient restClient;
  @Mock private RestClient.RequestHeadersUriSpec<?> deleteSpec;
  @Mock private RestClient.RequestHeadersSpec<?> headersSpec;
  @Mock private RestClient.ResponseSpec responseSpec;

  private SessionGatewayClient sessionGatewayClient;

  @SuppressWarnings("unchecked")
  @BeforeEach
  void setUp() {
    var properties = new SessionRevocationProperties(3, Duration.ZERO, 2.0, Duration.ZERO);
    sessionGatewayClient = new SessionGatewayClient(restClient, properties);
    when(restClient.delete()).thenReturn((RestClient.RequestHeadersUriSpec) deleteSpec);
    when(deleteSpec.uri(anyString(), any(Object[].class)))
        .thenReturn((RestClient.RequestHeadersSpec) headersSpec);
  }

  @Nested
  @DisplayName("revokeUserSessions")
  class RevokeUserSessionsTests {

    @Test
    @DisplayName("should return revoked on success")
    void shouldReturnRevokedOnSuccess() {
      // Arrange
      when(headersSpec.retrieve()).thenReturn(responseSpec);
      when(responseSpec.toBodilessEntity()).thenReturn(mock(ResponseEntity.class));

      // Act
      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result.revoked()).isTrue();
      assertThat(result.retryExhausted()).isFalse();
    }

    @Test
    @DisplayName("should return revoked after transient connection failure")
    void shouldReturnRevokedAfterTransientFailure() {
      // Arrange
      when(headersSpec.retrieve())
          .thenThrow(new ResourceAccessException("Connection refused"))
          .thenReturn(responseSpec);
      when(responseSpec.toBodilessEntity()).thenReturn(mock(ResponseEntity.class));

      // Act
      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result.revoked()).isTrue();
      assertThat(result.retryExhausted()).isFalse();
      verify(restClient, times(2)).delete();
    }

    @Test
    @DisplayName("should return revoked after transient server error")
    void shouldReturnRevokedAfterTransientServerError() {
      // Arrange
      var serverError = mock(RestClientResponseException.class);
      when(serverError.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
      when(serverError.getMessage()).thenReturn("Internal Server Error");
      when(headersSpec.retrieve()).thenReturn(responseSpec);
      when(responseSpec.toBodilessEntity())
          .thenThrow(serverError)
          .thenReturn(mock(ResponseEntity.class));

      // Act
      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result.revoked()).isTrue();
      assertThat(result.retryExhausted()).isFalse();
      verify(restClient, times(2)).delete();
    }

    @Test
    @DisplayName("should return retry exhausted on persistent connection failure")
    void shouldReturnRetryExhaustedOnPersistentFailure() {
      // Arrange
      when(headersSpec.retrieve()).thenThrow(new ResourceAccessException("Connection refused"));

      // Act
      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result.revoked()).isFalse();
      assertThat(result.retryExhausted()).isTrue();
      verify(restClient, times(3)).delete();
    }

    @Test
    @DisplayName("should not retry non-retryable client error")
    void shouldNotRetryNonRetryableClientError() {
      // Arrange
      var clientError = mock(RestClientResponseException.class);
      when(clientError.getStatusCode()).thenReturn(HttpStatus.BAD_REQUEST);
      when(headersSpec.retrieve()).thenReturn(responseSpec);
      when(responseSpec.toBodilessEntity()).thenThrow(clientError);

      // Act
      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result.revoked()).isFalse();
      assertThat(result.retryExhausted()).isFalse();
      verify(restClient).delete();
    }

    @Test
    @DisplayName("should return retry exhausted on persistent server error")
    void shouldReturnRetryExhaustedOnPersistentServerError() {
      // Arrange
      var serverError = mock(RestClientResponseException.class);
      when(serverError.getStatusCode()).thenReturn(HttpStatus.INTERNAL_SERVER_ERROR);
      when(serverError.getMessage()).thenReturn("Internal Server Error");
      when(headersSpec.retrieve()).thenReturn(responseSpec);
      when(responseSpec.toBodilessEntity()).thenThrow(serverError);

      // Act
      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result.revoked()).isFalse();
      assertThat(result.retryExhausted()).isTrue();
      verify(restClient, times(3)).delete();
    }
  }
}
