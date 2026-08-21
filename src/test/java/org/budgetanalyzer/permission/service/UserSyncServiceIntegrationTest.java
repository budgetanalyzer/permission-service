package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.service.exception.UserDeactivatedException;

@DisplayName("UserSyncService persistence integration")
class UserSyncServiceIntegrationTest extends PermissionServiceIntegrationTestSupport {

  @Autowired private UserSyncService userSyncService;

  @Test
  @DisplayName("creates a new user and assigns the seeded default role")
  void createsNewUserAndAssignsSeededDefaultRole() {
    var result =
        userSyncService.syncUser(
            TestConstants.TEST_IDP_SUB, TestConstants.TEST_EMAIL, TestConstants.TEST_DISPLAY_NAME);

    assertThat(result.getId()).matches("usr_[0-9a-f]{32}");
    assertThat(result.getIdpSub()).isEqualTo(TestConstants.TEST_IDP_SUB);
    assertThat(result.getEmail()).isEqualTo(TestConstants.TEST_EMAIL);
    assertThat(result.getDisplayName()).isEqualTo(TestConstants.TEST_DISPLAY_NAME);
    assertThat(userRepository.findById(result.getId()))
        .get()
        .extracting("idpSub", "email", "displayName")
        .containsExactly(
            TestConstants.TEST_IDP_SUB, TestConstants.TEST_EMAIL, TestConstants.TEST_DISPLAY_NAME);
    assertThat(userRoleRepository.findRoleIdsByUserId(result.getId()))
        .containsExactly(TestConstants.ROLE_USER);
  }

  @Test
  @DisplayName("updates mutable profile fields without changing role assignments")
  void updatesMutableProfileFieldsWithoutChangingRoleAssignments() {
    var existingUser =
        persistUser(
            TestConstants.TEST_USER_ID, TestConstants.TEST_IDP_SUB, "old@example.com", "Old Name");
    assignRoles(existingUser.getId(), TestConstants.ROLE_ADMIN);

    var result =
        userSyncService.syncUser(
            TestConstants.TEST_IDP_SUB, TestConstants.TEST_EMAIL, TestConstants.TEST_DISPLAY_NAME);

    assertThat(result.getId()).isEqualTo(TestConstants.TEST_USER_ID);
    assertThat(userRepository.findById(TestConstants.TEST_USER_ID))
        .get()
        .extracting("email", "displayName")
        .containsExactly(TestConstants.TEST_EMAIL, TestConstants.TEST_DISPLAY_NAME);
    assertThat(userRoleRepository.findRoleIdsByUserId(TestConstants.TEST_USER_ID))
        .containsExactly(TestConstants.ROLE_ADMIN);
  }

  @Test
  @DisplayName("rejects synchronization for a deactivated identity")
  void rejectsSynchronizationForDeactivatedIdentity() {
    var user =
        persistUser(
            TestConstants.TEST_USER_ID, TestConstants.TEST_IDP_SUB, "old@example.com", "Old Name");
    user.deactivate(TestConstants.TEST_ADMIN_ID);
    userRepository.saveAndFlush(user);

    assertThatThrownBy(
            () ->
                userSyncService.syncUser(
                    TestConstants.TEST_IDP_SUB,
                    TestConstants.TEST_EMAIL,
                    TestConstants.TEST_DISPLAY_NAME))
        .isInstanceOf(UserDeactivatedException.class);

    assertThat(userRepository.findById(TestConstants.TEST_USER_ID))
        .get()
        .extracting("email", "displayName")
        .containsExactly("old@example.com", "Old Name");
    assertThat(userRepository.count()).isEqualTo(2);
  }

  @Test
  @DisplayName("reuses an identity-provider subject after its active row is soft deleted")
  void reusesIdentityProviderSubjectAfterActiveRowIsSoftDeleted() {
    var deletedUser =
        persistUser(
            TestConstants.TEST_USER_ID, TestConstants.TEST_IDP_SUB, "old@example.com", "Old Name");
    deletedUser.markDeleted(TestConstants.TEST_ADMIN_ID);
    userRepository.saveAndFlush(deletedUser);

    var result =
        userSyncService.syncUser(
            TestConstants.TEST_IDP_SUB, TestConstants.TEST_EMAIL, TestConstants.TEST_DISPLAY_NAME);

    assertThat(result.getId()).isNotEqualTo(TestConstants.TEST_USER_ID);
    assertThat(userRepository.findByIdpSubAndDeletedFalse(TestConstants.TEST_IDP_SUB))
        .get()
        .extracting(User::getId)
        .isEqualTo(result.getId());
    assertThat(userRepository.count()).isEqualTo(3);
    assertThat(userRoleRepository.findRoleIdsByUserId(result.getId()))
        .containsExactly(TestConstants.ROLE_USER);
  }
}
