package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import org.budgetanalyzer.permission.TestConstants;

@DisplayName("PermissionService persistence integration")
class PermissionServiceIntegrationTest extends PermissionServiceIntegrationTestSupport {

  @Autowired private PermissionService permissionService;

  @Test
  @DisplayName("resolves effective permissions through seeded role mappings")
  void resolvesEffectivePermissionsThroughSeededRoleMappings() {
    persistUser(
        TestConstants.TEST_USER_ID,
        TestConstants.TEST_IDP_SUB,
        TestConstants.TEST_EMAIL,
        TestConstants.TEST_DISPLAY_NAME);
    assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_USER);

    var result = permissionService.getEffectivePermissions(TestConstants.TEST_USER_ID);

    assertThat(result.roles()).containsExactly(TestConstants.ROLE_USER);
    assertThat(result.permissions())
        .containsExactlyInAnyOrder(
            "transactions:read",
            "transactions:write",
            "transactions:delete",
            "views:read",
            "views:write",
            "views:delete",
            "statementformats:read",
            "statementformats:write",
            "currencies:read");
  }

  @Test
  @DisplayName("returns empty permissions when the user has no assigned roles")
  void returnsEmptyPermissionsWhenUserHasNoAssignedRoles() {
    persistUser(
        TestConstants.TEST_USER_ID,
        TestConstants.TEST_IDP_SUB,
        TestConstants.TEST_EMAIL,
        TestConstants.TEST_DISPLAY_NAME);

    var result = permissionService.getEffectivePermissions(TestConstants.TEST_USER_ID);

    assertThat(result.roles()).isEmpty();
    assertThat(result.permissions()).isEmpty();
  }
}
