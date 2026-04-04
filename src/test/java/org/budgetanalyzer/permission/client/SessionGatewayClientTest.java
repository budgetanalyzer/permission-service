package org.budgetanalyzer.permission.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import org.budgetanalyzer.permission.TestConstants;

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
    sessionGatewayClient = new SessionGatewayClient(restClient);
    when(restClient.delete()).thenReturn((RestClient.RequestHeadersUriSpec) deleteSpec);
    when(deleteSpec.uri(anyString(), any(Object[].class)))
        .thenReturn((RestClient.RequestHeadersSpec) headersSpec);
  }

  @Nested
  @DisplayName("revokeUserSessions")
  class RevokeUserSessionsTests {

    @Test
    @DisplayName("should return true on success")
    void shouldReturnTrueOnSuccess() {
      // Arrange
      when(headersSpec.retrieve()).thenReturn(responseSpec);
      when(responseSpec.toBodilessEntity()).thenReturn(mock(ResponseEntity.class));

      // Act
      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should return false on server error")
    void shouldReturnFalseOnServerError() {
      // Arrange
      when(headersSpec.retrieve()).thenReturn(responseSpec);
      when(responseSpec.toBodilessEntity()).thenThrow(mock(RestClientResponseException.class));

      // Act
      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should return false on connection failure")
    void shouldReturnFalseOnConnectionFailure() {
      // Arrange
      when(headersSpec.retrieve()).thenThrow(new ResourceAccessException("Connection refused"));

      // Act
      var result = sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result).isFalse();
    }
  }
}
