package org.budgetanalyzer.permission.api;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.service.PermissionServiceIntegrationTestSupport;

@AutoConfigureMockMvc
@DisplayName("InternalPermissionController")
class InternalPermissionControllerTest extends PermissionServiceIntegrationTestSupport {

  @Autowired private MockMvc mockMvc;

  @Nested
  @DisplayName("GET /internal/v1/users/{idpSub}/permissions")
  class GetUserPermissionsTests {

    @Test
    void shouldCreateUserAndReturnDefaultPermissionsWithoutClaimsHeaders() throws Exception {
      mockMvc
          .perform(
              get("/internal/v1/users/{idpSub}/permissions", TestConstants.TEST_IDP_SUB)
                  .param("email", TestConstants.TEST_EMAIL)
                  .param("displayName", TestConstants.TEST_DISPLAY_NAME))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId", matchesPattern("usr_[0-9a-f]{32}")))
          .andExpect(jsonPath("$.roles[0]").value(TestConstants.ROLE_USER))
          .andExpect(jsonPath("$.permissions", hasItem("transactions:read")))
          .andExpect(jsonPath("$.permissions", hasItem("transactions:write")));
    }

    @Test
    void shouldReturnExistingUserPermissions() throws Exception {
      persistUser(
          TestConstants.TEST_USER_ID,
          TestConstants.TEST_IDP_SUB,
          TestConstants.TEST_EMAIL,
          TestConstants.TEST_DISPLAY_NAME);
      assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_USER, TestConstants.ROLE_ADMIN);

      mockMvc
          .perform(
              get("/internal/v1/users/{idpSub}/permissions", TestConstants.TEST_IDP_SUB)
                  .param("email", TestConstants.TEST_EMAIL)
                  .param("displayName", TestConstants.TEST_DISPLAY_NAME))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(TestConstants.TEST_USER_ID))
          .andExpect(
              jsonPath(
                  "$.roles", containsInAnyOrder(TestConstants.ROLE_ADMIN, TestConstants.ROLE_USER)))
          .andExpect(jsonPath("$.permissions", hasItem(TestConstants.PERM_USERS_READ)))
          .andExpect(jsonPath("$.permissions", hasItem("views:read")));
    }

    @Test
    void shouldReturn422WhenUserIsDeactivated() throws Exception {
      var user =
          persistUser(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);
      user.deactivate(TestConstants.TEST_ADMIN_ID);
      userRepository.saveAndFlush(user);

      mockMvc
          .perform(
              get("/internal/v1/users/{idpSub}/permissions", TestConstants.TEST_IDP_SUB)
                  .param("email", TestConstants.TEST_EMAIL)
                  .param("displayName", TestConstants.TEST_DISPLAY_NAME))
          .andExpect(status().isUnprocessableEntity())
          .andExpect(jsonPath("$.type").value("APPLICATION_ERROR"))
          .andExpect(jsonPath("$.code").value("USER_DEACTIVATED"));
    }
  }
}
