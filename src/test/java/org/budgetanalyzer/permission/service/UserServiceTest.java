package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.api.request.UserFilter;
import org.budgetanalyzer.permission.client.SessionGatewayClient;
import org.budgetanalyzer.permission.client.SessionRevocationResult;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.domain.UserRole;
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
    lenient()
        .when(transactionTemplate.execute(any()))
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

  @Nested
  @DisplayName("search")
  class SearchTests {

    @Test
    @DisplayName("should batch role lookups for the current page")
    void shouldBatchRoleLookupsForTheCurrentPage() {
      var userRole = new UserRole();
      userRole.setUserId(TestConstants.TEST_USER_ID);
      userRole.setRoleId(TestConstants.ROLE_USER);
      var adminUserRole = new UserRole();
      adminUserRole.setUserId(TestConstants.TEST_ADMIN_ID);
      adminUserRole.setRoleId(TestConstants.ROLE_ADMIN);
      var secondAdminUserRole = new UserRole();
      secondAdminUserRole.setUserId(TestConstants.TEST_ADMIN_ID);
      secondAdminUserRole.setRoleId(TestConstants.ROLE_USER);
      var pageable = PageRequest.of(0, 2);
      var firstUser =
          new User(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);
      var secondUser =
          new User(
              TestConstants.TEST_ADMIN_ID,
              TestConstants.TEST_IDP_SUB_ADMIN,
              "admin@example.com",
              "Admin User");

      when(userRepository.findAllNotDeleted(any(), eq(pageable)))
          .thenReturn(
              new org.springframework.data.domain.PageImpl<>(
                  List.of(firstUser, secondUser), pageable, 3));
      when(userRoleRepository.findByUserIdIn(
              List.of(TestConstants.TEST_USER_ID, TestConstants.TEST_ADMIN_ID)))
          .thenReturn(List.of(secondAdminUserRole, adminUserRole, userRole));

      var result = userService.search(UserFilter.empty(), pageable);

      assertThat(result.getTotalElements()).isEqualTo(3);
      assertThat(result.getContent()).hasSize(2);
      assertThat(result.getContent().get(0).user().getId()).isEqualTo(TestConstants.TEST_USER_ID);
      assertThat(result.getContent().get(0).roleIds()).containsExactly(TestConstants.ROLE_USER);
      assertThat(result.getContent().get(1).roleIds())
          .containsExactly(TestConstants.ROLE_ADMIN, TestConstants.ROLE_USER);
    }

    @Test
    @DisplayName("should skip role lookup when search page is empty")
    void shouldSkipRoleLookupWhenSearchPageIsEmpty() {
      var pageable = PageRequest.of(0, 5);
      when(userRepository.findAllNotDeleted(any(), eq(pageable)))
          .thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(), pageable, 0));

      var result = userService.search(UserFilter.empty(), pageable);

      assertThat(result.getContent()).isEmpty();
      verify(userRoleRepository, never()).findByUserIdIn(any());
    }
  }

  @Nested
  @DisplayName("getUserWithRoles")
  class GetUserWithRolesTests {

    @Test
    @DisplayName("should return user with sorted role IDs")
    void shouldReturnUserWithSortedRoleIds() {
      var user =
          new User(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);
      when(userRepository.findByIdNotDeleted(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));
      when(userRoleRepository.findRoleIdsByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(Set.of(TestConstants.ROLE_USER, TestConstants.ROLE_ADMIN));

      var result = userService.getUserWithRoles(TestConstants.TEST_USER_ID);

      assertThat(result.user()).isSameAs(user);
      assertThat(result.roleIds())
          .containsExactly(TestConstants.ROLE_ADMIN, TestConstants.ROLE_USER);
    }
  }

  @Nested
  @DisplayName("getUserDetail")
  class GetUserDetailTests {

    @Test
    @DisplayName("should return null actor references when audit fields are empty")
    void shouldReturnNullActorReferencesWhenAuditFieldsAreEmpty() {
      var user =
          new User(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);
      when(userRepository.findByIdNotDeleted(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));
      when(userRoleRepository.findRoleIdsByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(Set.of(TestConstants.ROLE_USER));

      var result = userService.getUserDetail(TestConstants.TEST_USER_ID);

      assertThat(result.user()).isSameAs(user);
      assertThat(result.roleIds()).containsExactly(TestConstants.ROLE_USER);
      assertThat(result.deactivatedBy()).isNull();
      assertThat(result.deletedBy()).isNull();
      verify(userRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("should resolve deactivatedBy and leave deletedBy null")
    void shouldResolveDeactivatedByAndLeaveDeletedByNull() {
      var user =
          new User(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);
      user.deactivate(TestConstants.TEST_ADMIN_ID);
      var adminUser =
          new User(
              TestConstants.TEST_ADMIN_ID,
              TestConstants.TEST_IDP_SUB_ADMIN,
              "admin@example.com",
              "Admin User");

      when(userRepository.findByIdNotDeleted(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));
      when(userRoleRepository.findRoleIdsByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(Set.of(TestConstants.ROLE_USER));
      when(userRepository.findAllById(Set.of(TestConstants.TEST_ADMIN_ID)))
          .thenReturn(List.of(adminUser));

      var result = userService.getUserDetail(TestConstants.TEST_USER_ID);

      assertThat(result.deactivatedBy())
          .extracting("id", "displayName", "email")
          .containsExactly(TestConstants.TEST_ADMIN_ID, "Admin User", "admin@example.com");
      assertThat(result.deletedBy()).isNull();

      verify(userRepository)
          .findAllById(
              org.mockito.ArgumentMatchers.argThat(
                  actorIds ->
                      actorIds != null
                          && StreamSupport.stream(actorIds.spliterator(), false)
                              .toList()
                              .equals(List.of(TestConstants.TEST_ADMIN_ID))));
    }

    @Test
    @DisplayName("should batch resolve both actor references when they share the same actor")
    void shouldBatchResolveBothActorReferencesWhenTheyShareTheSameActor() {
      var user =
          new User(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);
      user.deactivate(TestConstants.TEST_ADMIN_ID);
      user.markDeleted(TestConstants.TEST_ADMIN_ID);
      var adminUser =
          new User(
              TestConstants.TEST_ADMIN_ID,
              TestConstants.TEST_IDP_SUB_ADMIN,
              "admin@example.com",
              "Admin User");

      when(userRepository.findByIdNotDeleted(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));
      when(userRoleRepository.findRoleIdsByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(Set.of(TestConstants.ROLE_ADMIN, TestConstants.ROLE_USER));
      when(userRepository.findAllById(Set.of(TestConstants.TEST_ADMIN_ID)))
          .thenReturn(List.of(adminUser));

      var result = userService.getUserDetail(TestConstants.TEST_USER_ID);

      assertThat(result.deactivatedBy()).isEqualTo(result.deletedBy());
      verify(userRepository, times(1)).findAllById(any());
    }

    @Test
    @DisplayName("should reuse target user when actor is self")
    void shouldReuseTargetUserWhenActorIsSelf() {
      var user =
          new User(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);
      user.deactivate(TestConstants.TEST_USER_ID);
      user.markDeleted(TestConstants.TEST_USER_ID);

      when(userRepository.findByIdNotDeleted(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));
      when(userRoleRepository.findRoleIdsByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(Set.of(TestConstants.ROLE_USER));

      var result = userService.getUserDetail(TestConstants.TEST_USER_ID);

      assertThat(result.deactivatedBy())
          .extracting("id", "displayName", "email")
          .containsExactly(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_DISPLAY_NAME,
              TestConstants.TEST_EMAIL);
      assertThat(result.deletedBy())
          .extracting("id", "displayName", "email")
          .containsExactly(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_DISPLAY_NAME,
              TestConstants.TEST_EMAIL);
      verify(userRepository, never()).findAllById(any());
    }

    @Test
    @DisplayName("should resolve soft-deleted actor details")
    void shouldResolveSoftDeletedActorDetails() {
      var user =
          new User(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);
      user.deactivate(TestConstants.TEST_ADMIN_ID);
      var adminUser =
          new User(
              TestConstants.TEST_ADMIN_ID,
              TestConstants.TEST_IDP_SUB_ADMIN,
              "admin@example.com",
              "Admin User");
      adminUser.markDeleted("usr_auditor789");

      when(userRepository.findByIdNotDeleted(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));
      when(userRoleRepository.findRoleIdsByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(Set.of(TestConstants.ROLE_USER));
      when(userRepository.findAllById(Set.of(TestConstants.TEST_ADMIN_ID)))
          .thenReturn(List.of(adminUser));

      var result = userService.getUserDetail(TestConstants.TEST_USER_ID);

      assertThat(result.deactivatedBy())
          .extracting("id", "displayName", "email")
          .containsExactly(TestConstants.TEST_ADMIN_ID, "Admin User", "admin@example.com");
    }

    @Test
    @DisplayName("should fall back to id-only reference when actor is missing")
    void shouldFallBackToIdOnlyReferenceWhenActorIsMissing() {
      var missingActorId = "usr_missing999";
      var user =
          new User(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);
      user.deactivate(missingActorId);

      when(userRepository.findByIdNotDeleted(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));
      when(userRoleRepository.findRoleIdsByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(Set.of(TestConstants.ROLE_USER));
      when(userRepository.findAllById(Set.of(missingActorId))).thenReturn(List.of());

      var result = userService.getUserDetail(TestConstants.TEST_USER_ID);

      assertThat(result.deactivatedBy())
          .extracting("id", "displayName", "email")
          .containsExactly(missingActorId, null, null);
      assertThat(result.deletedBy()).isNull();
    }
  }
}
