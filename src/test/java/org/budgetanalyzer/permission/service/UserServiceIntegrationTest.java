package org.budgetanalyzer.permission.service;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.deleteRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.api.request.UserFilter;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.domain.UserStatus;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceUnavailableException;

@DisplayName("UserService persistence integration")
class UserServiceIntegrationTest extends PermissionServiceIntegrationTestSupport {

  private static final String REVOCATION_PATH =
      "/session-gateway/internal/v1/sessions/users/" + TestConstants.TEST_USER_ID;

  @Autowired private UserService userService;

  @Nested
  @DisplayName("deactivateUser")
  class DeactivateUserTests {

    @Test
    void deactivatesUserAndRemovesRolesBeforeRevokingSessions() {
      persistTestUser();
      assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_USER, TestConstants.ROLE_ADMIN);
      wireMockServer.stubFor(delete(urlEqualTo(REVOCATION_PATH)).willReturn(noContent()));

      var result =
          userService.deactivateUser(TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY);

      assertThat(result.userId()).isEqualTo(TestConstants.TEST_USER_ID);
      assertThat(result.status()).isEqualTo(UserStatus.DEACTIVATED);
      assertThat(result.rolesRemoved()).isEqualTo(2);
      assertThat(result.sessionsRevoked()).isTrue();
      assertThat(userRoleRepository.findByUserId(TestConstants.TEST_USER_ID)).isEmpty();
      assertThat(userRepository.findById(TestConstants.TEST_USER_ID))
          .get()
          .satisfies(
              persistedUser -> {
                assertThat(persistedUser.isDeactivated()).isTrue();
                assertThat(persistedUser.getDeactivatedBy())
                    .isEqualTo(TestConstants.TEST_DEACTIVATED_BY);
                assertThat(persistedUser.getDeactivatedAt()).isNotNull();
              });
      wireMockServer.verify(1, deleteRequestedFor(urlEqualTo(REVOCATION_PATH)));
    }

    @Test
    void repeatsSessionRevocationWithoutChangingAnAlreadyDeactivatedUser() {
      var user = persistTestUser();
      user.deactivate(TestConstants.TEST_DEACTIVATED_BY);
      userRepository.saveAndFlush(user);
      wireMockServer.stubFor(delete(urlEqualTo(REVOCATION_PATH)).willReturn(noContent()));

      var result =
          userService.deactivateUser(TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY);

      assertThat(result.status()).isEqualTo(UserStatus.DEACTIVATED);
      assertThat(result.rolesRemoved()).isZero();
      assertThat(result.sessionsRevoked()).isTrue();
      wireMockServer.verify(1, deleteRequestedFor(urlEqualTo(REVOCATION_PATH)));
    }

    @Test
    void rejectsMissingAndSoftDeletedUsersWithoutCallingSessionGateway() {
      assertThatThrownBy(
              () ->
                  userService.deactivateUser(
                      TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY))
          .isInstanceOf(ResourceNotFoundException.class);

      var deletedUser = persistTestUser();
      deletedUser.markDeleted(TestConstants.TEST_ADMIN_ID);
      userRepository.saveAndFlush(deletedUser);

      assertThatThrownBy(
              () ->
                  userService.deactivateUser(
                      TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY))
          .isInstanceOf(ResourceNotFoundException.class);
      wireMockServer.verify(0, deleteRequestedFor(urlEqualTo(REVOCATION_PATH)));
    }

    @Test
    void keepsCommittedDeactivationWhenTransientRevocationRetriesAreExhausted() {
      persistTestUser();
      assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_USER);
      wireMockServer.stubFor(
          delete(urlEqualTo(REVOCATION_PATH)).willReturn(aResponse().withStatus(503)));

      assertThatThrownBy(
              () ->
                  userService.deactivateUser(
                      TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY))
          .isInstanceOf(ServiceUnavailableException.class);

      assertThat(userRepository.findById(TestConstants.TEST_USER_ID))
          .get()
          .extracting(User::getStatus, User::getDeactivatedBy)
          .containsExactly(UserStatus.DEACTIVATED, TestConstants.TEST_DEACTIVATED_BY);
      assertThat(userRoleRepository.findByUserId(TestConstants.TEST_USER_ID)).isEmpty();
      wireMockServer.verify(3, deleteRequestedFor(urlEqualTo(REVOCATION_PATH)));
    }

    @Test
    void keepsCommittedDeactivationWhenRevocationFailsWithoutRetry() {
      persistTestUser();
      assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_USER);
      wireMockServer.stubFor(
          delete(urlEqualTo(REVOCATION_PATH)).willReturn(aResponse().withStatus(400)));

      assertThatThrownBy(
              () ->
                  userService.deactivateUser(
                      TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY))
          .isInstanceOf(ServiceUnavailableException.class);

      assertThat(userRepository.findById(TestConstants.TEST_USER_ID))
          .get()
          .extracting(User::getStatus, User::getDeactivatedBy)
          .containsExactly(UserStatus.DEACTIVATED, TestConstants.TEST_DEACTIVATED_BY);
      assertThat(userRoleRepository.findByUserId(TestConstants.TEST_USER_ID)).isEmpty();
      wireMockServer.verify(1, deleteRequestedFor(urlEqualTo(REVOCATION_PATH)));
    }
  }

  @Nested
  @DisplayName("search")
  class SearchTests {

    @Test
    void returnsPersistedUsersWithSortedRolesAndPageMetadata() {
      persistUser("usr_search01", "oidc|search-1", "one@example.com", "One");
      persistUser("usr_search02", "oidc|search-2", "two@example.com", "Two");
      persistUser("usr_search03", "oidc|search-3", "three@example.com", "Three");
      assignRoles("usr_search01", TestConstants.ROLE_USER);
      assignRoles("usr_search02", TestConstants.ROLE_USER, TestConstants.ROLE_ADMIN);
      var pageable = PageRequest.of(0, 2, Sort.by("id"));

      var result = userService.search(UserFilter.empty(), pageable);

      assertThat(result.getTotalElements()).isEqualTo(3);
      assertThat(result.getContent())
          .extracting(userWithRoles -> userWithRoles.user().getId())
          .containsExactly("usr_search01", "usr_search02");
      assertThat(result.getContent().get(0).roleIds()).containsExactly(TestConstants.ROLE_USER);
      assertThat(result.getContent().get(1).roleIds())
          .containsExactly(TestConstants.ROLE_ADMIN, TestConstants.ROLE_USER);
    }

    @Test
    void returnsAnEmptyPageWhenNoPersistedUsersMatch() {
      var result = userService.search(UserFilter.empty(), PageRequest.of(0, 5));

      assertThat(result.getContent()).isEmpty();
      assertThat(result.getTotalElements()).isZero();
    }
  }

  @Test
  void returnsUserWithSortedRoleIds() {
    persistTestUser();
    assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_USER, TestConstants.ROLE_ADMIN);

    var result = userService.getUserWithRoles(TestConstants.TEST_USER_ID);

    assertThat(result.user().getId()).isEqualTo(TestConstants.TEST_USER_ID);
    assertThat(result.roleIds()).containsExactly(TestConstants.ROLE_ADMIN, TestConstants.ROLE_USER);
  }

  @Nested
  @DisplayName("getUserDetail")
  class GetUserDetailTests {

    @Test
    void returnsNullActorReferencesWhenAuditFieldsAreEmpty() {
      persistTestUser();
      assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_USER);

      var result = userService.getUserDetail(TestConstants.TEST_USER_ID);

      assertThat(result.roleIds()).containsExactly(TestConstants.ROLE_USER);
      assertThat(result.deactivatedBy()).isNull();
      assertThat(result.deletedBy()).isNull();
    }

    @Test
    void resolvesPersistedDeactivationActorIncludingSoftDeletedActors() {
      var admin =
          persistUser(
              TestConstants.TEST_ADMIN_ID,
              TestConstants.TEST_IDP_SUB_ADMIN,
              "admin@example.com",
              "Admin User");
      var user = persistTestUser();
      user.deactivate(TestConstants.TEST_ADMIN_ID);
      userRepository.saveAndFlush(user);
      admin.markDeleted("usr_auditor789");
      userRepository.saveAndFlush(admin);

      var result = userService.getUserDetail(TestConstants.TEST_USER_ID);

      assertThat(result.deactivatedBy())
          .extracting("id", "displayName", "email")
          .containsExactly(TestConstants.TEST_ADMIN_ID, "Admin User", "admin@example.com");
      assertThat(result.deletedBy()).isNull();
    }

    @Test
    void resolvesSelfAsDeactivationActorFromTheTargetUser() {
      var user = persistTestUser();
      user.deactivate(TestConstants.TEST_USER_ID);
      userRepository.saveAndFlush(user);

      var result = userService.getUserDetail(TestConstants.TEST_USER_ID);

      assertThat(result.deactivatedBy())
          .extracting("id", "displayName", "email")
          .containsExactly(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_DISPLAY_NAME,
              TestConstants.TEST_EMAIL);
    }

    @Test
    void fallsBackToAnIdOnlyReferenceWhenTheActorNoLongerExists() {
      var missingActorId = "usr_missing999";
      var user = persistTestUser();
      user.deactivate(missingActorId);
      userRepository.saveAndFlush(user);

      var result = userService.getUserDetail(TestConstants.TEST_USER_ID);

      assertThat(result.deactivatedBy())
          .extracting("id", "displayName", "email")
          .containsExactly(missingActorId, null, null);
      assertThat(result.deletedBy()).isNull();
    }
  }

  private User persistTestUser() {
    return persistUser(
        TestConstants.TEST_USER_ID,
        TestConstants.TEST_IDP_SUB,
        TestConstants.TEST_EMAIL,
        TestConstants.TEST_DISPLAY_NAME);
  }
}
