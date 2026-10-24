package org.budgetanalyzer.permission.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.service.PermissionServiceIntegrationTestSupport;

@AutoConfigureMockMvc
@DisplayName("InternalPermissionController")
class InternalPermissionControllerIntegrationTest extends PermissionServiceIntegrationTestSupport {

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
          .andExpect(status().isUnprocessableContent())
          .andExpect(content().contentType(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.type").value("APPLICATION_ERROR"))
          .andExpect(jsonPath("$.message").isString())
          .andExpect(jsonPath("$.code").value("USER_DEACTIVATED"))
          .andExpect(jsonPath("$.fieldErrors").doesNotExist());
    }
  }

  @Nested
  @DisplayName("GET /internal/v1/users/{userId}/authorization")
  class GetUserAuthorizationTests {

    @Test
    void shouldReturnCurrentChangedGrantsForActiveUserWithoutClaimsHeaders() throws Exception {
      persistTestUser();
      assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_USER);

      mockMvc
          .perform(get("/internal/v1/users/{userId}/authorization", TestConstants.TEST_USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("ACTIVE"))
          .andExpect(jsonPath("$.roles[0]").value(TestConstants.ROLE_USER));

      assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_ADMIN);

      mockMvc
          .perform(get("/internal/v1/users/{userId}/authorization", TestConstants.TEST_USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(TestConstants.TEST_USER_ID))
          .andExpect(jsonPath("$.status").value("ACTIVE"))
          .andExpect(
              jsonPath(
                  "$.roles", containsInAnyOrder(TestConstants.ROLE_ADMIN, TestConstants.ROLE_USER)))
          .andExpect(jsonPath("$.permissions", hasItem(TestConstants.PERM_USERS_READ)));
    }

    @Test
    void shouldKeepActiveStatusWhenUserHasNoGrants() throws Exception {
      persistTestUser();

      mockMvc
          .perform(get("/internal/v1/users/{userId}/authorization", TestConstants.TEST_USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(TestConstants.TEST_USER_ID))
          .andExpect(jsonPath("$.status").value("ACTIVE"))
          .andExpect(jsonPath("$.roles").isEmpty())
          .andExpect(jsonPath("$.permissions").isEmpty());
    }

    @Test
    void shouldReturnNoGrantsForDeactivatedUserWithLeftoverRoles() throws Exception {
      var user = persistTestUser();
      assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_USER);
      user.deactivate(TestConstants.TEST_ADMIN_ID);
      userRepository.saveAndFlush(user);

      mockMvc
          .perform(get("/internal/v1/users/{userId}/authorization", TestConstants.TEST_USER_ID))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.status").value("DEACTIVATED"))
          .andExpect(jsonPath("$.roles").isEmpty())
          .andExpect(jsonPath("$.permissions").isEmpty());

      assertThat(userRoleRepository.findByUserId(TestConstants.TEST_USER_ID)).hasSize(1);
    }

    @Test
    void shouldReturn404ForMissingAndSoftDeletedUsers() throws Exception {
      mockMvc
          .perform(get("/internal/v1/users/{userId}/authorization", TestConstants.TEST_USER_ID))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("NOT_FOUND"));

      var user = persistTestUser();
      user.markDeleted(TestConstants.TEST_ADMIN_ID);
      userRepository.saveAndFlush(user);

      mockMvc
          .perform(get("/internal/v1/users/{userId}/authorization", TestConstants.TEST_USER_ID))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("NOT_FOUND"));
    }

    @Test
    void shouldNotModifyUserProfileAuditFieldsOrRoleAssignments() throws Exception {
      var user = persistTestUser();
      assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_USER);
      var persistedUserBeforeLookup = userRepository.findById(user.getId()).orElseThrow();
      var userRoleBeforeLookup =
          userRoleRepository.findByUserId(TestConstants.TEST_USER_ID).getFirst();

      mockMvc
          .perform(get("/internal/v1/users/{userId}/authorization", TestConstants.TEST_USER_ID))
          .andExpect(status().isOk());

      assertThat(userRepository.findAll()).hasSize(2);
      assertThat(userRepository.findById(TestConstants.TEST_USER_ID))
          .get()
          .satisfies(
              persistedUser -> {
                assertThat(persistedUser.getIdpSub()).isEqualTo(TestConstants.TEST_IDP_SUB);
                assertThat(persistedUser.getEmail()).isEqualTo(TestConstants.TEST_EMAIL);
                assertThat(persistedUser.getDisplayName())
                    .isEqualTo(TestConstants.TEST_DISPLAY_NAME);
                assertThat(persistedUser.getCreatedAt())
                    .isEqualTo(persistedUserBeforeLookup.getCreatedAt());
                assertThat(persistedUser.getUpdatedAt())
                    .isEqualTo(persistedUserBeforeLookup.getUpdatedAt());
              });
      assertThat(userRoleRepository.findByUserId(TestConstants.TEST_USER_ID))
          .singleElement()
          .satisfies(
              persistedUserRole -> {
                assertThat(persistedUserRole.getId()).isEqualTo(userRoleBeforeLookup.getId());
                assertThat(persistedUserRole.getCreatedAt())
                    .isEqualTo(userRoleBeforeLookup.getCreatedAt());
              });
    }

    @Test
    void shouldRequireClaimsHeadersForOtherMethodsAndUnrelatedInternalRoutes() throws Exception {
      mockMvc
          .perform(post("/internal/v1/users/{userId}/authorization", TestConstants.TEST_USER_ID))
          .andExpect(status().isUnauthorized())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
          .andExpect(jsonPath("$.type").value("UNAUTHORIZED"))
          .andExpect(jsonPath("$.message").isString())
          .andExpect(jsonPath("$.code").doesNotExist())
          .andExpect(jsonPath("$.fieldErrors").doesNotExist());

      mockMvc
          .perform(
              get("/internal/v1/users/{userId}/authorization/details", TestConstants.TEST_USER_ID))
          .andExpect(status().isUnauthorized());
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
