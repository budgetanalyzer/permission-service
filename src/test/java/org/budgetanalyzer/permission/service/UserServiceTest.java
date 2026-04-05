package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.client.SessionGatewayClient;
import org.budgetanalyzer.permission.client.SessionRevocationResult;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.domain.UserStatus;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceUnavailableException;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserService")
class UserServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private UserRoleRepository userRoleRepository;
  @Mock private SessionGatewayClient sessionGatewayClient;
  @Mock private TransactionTemplate transactionTemplate;

  private UserService userService;

  @BeforeEach
  void setUp() {
    userService =
        new UserService(
            userRepository, userRoleRepository, sessionGatewayClient, transactionTemplate);
    when(transactionTemplate.execute(any()))
        .thenAnswer(
            invocationOnMock -> {
              var transactionCallback = invocationOnMock.getArgument(0, TransactionCallback.class);
              return transactionCallback.doInTransaction(null);
            });
  }

  @Nested
  @DisplayName("deactivateUser")
  class DeactivateUserTests {

    @Test
    @DisplayName("should deactivate active user")
    void shouldDeactivateActiveUser() {
      // Arrange
      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      user.setIdpSub(TestConstants.TEST_IDP_SUB);

      when(userRepository.findById(TestConstants.TEST_USER_ID)).thenReturn(Optional.of(user));
      when(userRoleRepository.deleteByUserId(TestConstants.TEST_USER_ID)).thenReturn(2);
      when(sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID))
          .thenReturn(new SessionRevocationResult(true, false));

      // Act
      var result =
          userService.deactivateUser(TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY);

      // Assert
      assertThat(result.userId()).isEqualTo(TestConstants.TEST_USER_ID);
      assertThat(result.status()).isEqualTo(UserStatus.DEACTIVATED);
      assertThat(result.rolesRemoved()).isEqualTo(2);
      assertThat(result.sessionsRevoked()).isTrue();

      var captor = ArgumentCaptor.forClass(User.class);
      verify(userRepository).save(captor.capture());
      var savedUser = captor.getValue();
      assertThat(savedUser.isDeactivated()).isTrue();
      assertThat(savedUser.getDeactivatedBy()).isEqualTo(TestConstants.TEST_DEACTIVATED_BY);
      assertThat(savedUser.getDeactivatedAt()).isNotNull();

      var inOrder = inOrder(transactionTemplate, sessionGatewayClient);
      inOrder.verify(transactionTemplate).execute(any());
      inOrder.verify(sessionGatewayClient).revokeUserSessions(TestConstants.TEST_USER_ID);
    }

    @Test
    @DisplayName("should be idempotent for already deactivated user")
    void shouldBeIdempotentForDeactivatedUser() {
      // Arrange
      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      user.setIdpSub(TestConstants.TEST_IDP_SUB);
      user.deactivate(TestConstants.TEST_DEACTIVATED_BY);

      when(userRepository.findById(TestConstants.TEST_USER_ID)).thenReturn(Optional.of(user));
      when(sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID))
          .thenReturn(new SessionRevocationResult(true, false));

      // Act
      var result =
          userService.deactivateUser(TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY);

      // Assert
      assertThat(result.userId()).isEqualTo(TestConstants.TEST_USER_ID);
      assertThat(result.status()).isEqualTo(UserStatus.DEACTIVATED);
      assertThat(result.rolesRemoved()).isZero();
      assertThat(result.sessionsRevoked()).isTrue();

      verify(userRepository, never()).save(any());
      verify(userRoleRepository, never()).deleteByUserId(any());
    }

    @Test
    @DisplayName("should throw when user not found")
    void shouldThrowWhenUserNotFound() {
      // Arrange
      when(userRepository.findById(TestConstants.TEST_USER_ID)).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(
              () ->
                  userService.deactivateUser(
                      TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining(TestConstants.TEST_USER_ID);

      verify(sessionGatewayClient, never()).revokeUserSessions(any());
    }

    @Test
    @DisplayName("should throw when user is soft-deleted")
    void shouldThrowWhenUserIsSoftDeleted() {
      // Arrange
      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      user.setIdpSub(TestConstants.TEST_IDP_SUB);
      user.markDeleted(TestConstants.TEST_ADMIN_ID);

      when(userRepository.findById(TestConstants.TEST_USER_ID)).thenReturn(Optional.of(user));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  userService.deactivateUser(
                      TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining(TestConstants.TEST_USER_ID);

      verify(sessionGatewayClient, never()).revokeUserSessions(any());
    }

    @Test
    @DisplayName(
        "should throw ServiceUnavailableException when session revocation exhausts retries")
    void shouldThrowWhenSessionRevocationExhaustsRetries() {
      // Arrange
      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      user.setIdpSub(TestConstants.TEST_IDP_SUB);

      when(userRepository.findById(TestConstants.TEST_USER_ID)).thenReturn(Optional.of(user));
      when(userRoleRepository.deleteByUserId(TestConstants.TEST_USER_ID)).thenReturn(1);
      when(sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID))
          .thenReturn(new SessionRevocationResult(false, true));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  userService.deactivateUser(
                      TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY))
          .isInstanceOf(ServiceUnavailableException.class)
          .hasMessageContaining(TestConstants.TEST_USER_ID)
          .hasMessageContaining("retry is safe");

      // Verify deactivation was persisted before revocation attempt
      var captor = ArgumentCaptor.forClass(User.class);
      verify(userRepository).save(captor.capture());
      assertThat(captor.getValue().isDeactivated()).isTrue();
    }

    @Test
    @DisplayName(
        "should throw ServiceUnavailableException when session revocation fails"
            + " with non-retryable error")
    void shouldThrowWhenSessionRevocationFailsNonRetryable() {
      // Arrange
      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      user.setIdpSub(TestConstants.TEST_IDP_SUB);

      when(userRepository.findById(TestConstants.TEST_USER_ID)).thenReturn(Optional.of(user));
      when(userRoleRepository.deleteByUserId(TestConstants.TEST_USER_ID)).thenReturn(1);
      when(sessionGatewayClient.revokeUserSessions(TestConstants.TEST_USER_ID))
          .thenReturn(new SessionRevocationResult(false, false));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  userService.deactivateUser(
                      TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY))
          .isInstanceOf(ServiceUnavailableException.class)
          .hasMessageContaining(TestConstants.TEST_USER_ID);

      verify(userRepository).save(any());
    }
  }
}
