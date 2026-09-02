package org.budgetanalyzer.permission.api;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.service.PermissionServiceIntegrationTestSupport;
import org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder;

@AutoConfigureMockMvc
@DisplayName("UserController")
class UserControllerIntegrationTest extends PermissionServiceIntegrationTestSupport {

  private static final String REVOCATION_PATH =
      "/session-gateway/internal/v1/sessions/users/" + TestConstants.TEST_USER_ID;

  @Autowired private MockMvc mockMvc;

  @Nested
  @DisplayName("GET /v1/users")
  class GetUsersTests {

    @Test
    void shouldReturnPagedUsersWhenFilterAndSortAreValid() throws Exception {
      persistUser("usr_admin001", "oidc|admin-1", "admin.alpha@example.com", "Alpha Admin");
      persistUser("usr_admin002", "oidc|admin-2", "admin.bravo@example.com", "Bravo Admin");
      persistUser("usr_regular003", "oidc|regular-3", "regular@example.com", "Regular User");
      assignRoles("usr_admin001", TestConstants.ROLE_USER);
      assignRoles("usr_admin002", TestConstants.ROLE_ADMIN, TestConstants.ROLE_USER);

      mockMvc
          .perform(
              get("/v1/users")
                  .queryParam("email", "admin")
                  .queryParam("status", "ACTIVE")
                  .queryParam("page", "0")
                  .queryParam("size", "5")
                  .queryParam("sort", "email,asc")
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_USERS_READ)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[0].id").value("usr_admin001"))
          .andExpect(jsonPath("$.content[0].roleIds[0]").value(TestConstants.ROLE_USER))
          .andExpect(jsonPath("$.content[1].id").value("usr_admin002"))
          .andExpect(jsonPath("$.content[1].roleIds[0]").value(TestConstants.ROLE_ADMIN))
          .andExpect(jsonPath("$.content[1].roleIds[1]").value(TestConstants.ROLE_USER))
          .andExpect(jsonPath("$.metadata.page").value(0))
          .andExpect(jsonPath("$.metadata.size").value(5))
          .andExpect(jsonPath("$.metadata.totalElements").value(2));
    }

    @Test
    void shouldReturn400WhenSortFieldIsInvalid() throws Exception {
      mockMvc
          .perform(
              get("/v1/users")
                  .queryParam("sort", "bogus,asc")
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_USERS_READ)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("INVALID_REQUEST"));
    }

    @Test
    void shouldReturn403WhenLackingUsersReadPermission() throws Exception {
      mockMvc
          .perform(
              get("/v1/users")
                  .with(ClaimsHeaderTestBuilder.user(TestConstants.TEST_USER_ID).withPermissions()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("GET /v1/users/{id}")
  class GetUserTests {

    @Test
    void shouldReturnUserDetailsWithRoles() throws Exception {
      persistAdminUser();
      var user = persistTestUser();
      user.deactivate(TestConstants.TEST_ADMIN_ID);
      userRepository.saveAndFlush(user);
      assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_ADMIN, TestConstants.ROLE_USER);

      mockMvc
          .perform(
              get("/v1/users/{id}", TestConstants.TEST_USER_ID)
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_USERS_READ)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value(TestConstants.TEST_USER_ID))
          .andExpect(jsonPath("$.roleIds[0]").value(TestConstants.ROLE_ADMIN))
          .andExpect(jsonPath("$.roleIds[1]").value(TestConstants.ROLE_USER))
          .andExpect(jsonPath("$.status").value("DEACTIVATED"))
          .andExpect(jsonPath("$.deactivatedBy.id").value(TestConstants.TEST_ADMIN_ID))
          .andExpect(jsonPath("$.deactivatedBy.displayName").value("Admin User"))
          .andExpect(jsonPath("$.deactivatedBy.email").value("admin@example.com"))
          .andExpect(jsonPath("$.deletedBy").doesNotExist());
    }

    @Test
    void shouldReturnDegradedActorReferenceWhenActorIsUnresolved() throws Exception {
      var user = persistTestUser();
      user.deactivate("usr_missing999");
      userRepository.saveAndFlush(user);

      mockMvc
          .perform(
              get("/v1/users/{id}", TestConstants.TEST_USER_ID)
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_USERS_READ)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.deactivatedBy.id").value("usr_missing999"))
          .andExpect(jsonPath("$.deactivatedBy.displayName").doesNotExist())
          .andExpect(jsonPath("$.deactivatedBy.email").doesNotExist());
    }

    @Test
    void shouldReturn404WhenUserNotFound() throws Exception {
      mockMvc
          .perform(
              get("/v1/users/{id}", TestConstants.TEST_USER_ID)
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_USERS_READ)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403WhenLackingUsersReadPermission() throws Exception {
      mockMvc
          .perform(
              get("/v1/users/{id}", TestConstants.TEST_USER_ID)
                  .with(ClaimsHeaderTestBuilder.user(TestConstants.TEST_USER_ID).withPermissions()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("POST /v1/users/{id}/deactivate")
  class DeactivateUserTests {

    @Test
    void shouldDeactivateUser() throws Exception {
      persistAdminUser();
      persistTestUser();
      assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_USER, TestConstants.ROLE_ADMIN);
      wireMockServer.stubFor(delete(urlEqualTo(REVOCATION_PATH)).willReturn(noContent()));

      mockMvc
          .perform(
              post("/v1/users/{id}/deactivate", TestConstants.TEST_USER_ID)
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_USERS_WRITE)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(TestConstants.TEST_USER_ID))
          .andExpect(jsonPath("$.status").value("DEACTIVATED"))
          .andExpect(jsonPath("$.rolesRemoved").value(2))
          .andExpect(jsonPath("$.sessionsRevoked").value(true));
    }

    @Test
    void shouldReturn404WhenUserNotFound() throws Exception {
      mockMvc
          .perform(
              post("/v1/users/{id}/deactivate", TestConstants.TEST_USER_ID)
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_USERS_WRITE)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("NOT_FOUND"));
    }

    @Test
    void shouldReturn403WhenLackingPermission() throws Exception {
      mockMvc
          .perform(
              post("/v1/users/{id}/deactivate", TestConstants.TEST_USER_ID)
                  .with(ClaimsHeaderTestBuilder.user(TestConstants.TEST_USER_ID).withPermissions()))
          .andExpect(status().isForbidden());
    }

    @Test
    void shouldReturn503WhenSessionRevocationFails() throws Exception {
      persistAdminUser();
      persistTestUser();
      assignRoles(TestConstants.TEST_USER_ID, TestConstants.ROLE_USER);
      wireMockServer.stubFor(
          delete(urlEqualTo(REVOCATION_PATH)).willReturn(aResponse().withStatus(503)));

      mockMvc
          .perform(
              post("/v1/users/{id}/deactivate", TestConstants.TEST_USER_ID)
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_USERS_WRITE)))
          .andExpect(status().isServiceUnavailable())
          .andExpect(jsonPath("$.type").value("SERVICE_UNAVAILABLE"));
    }
  }

  private User persistTestUser() {
    return persistUser(
        TestConstants.TEST_USER_ID,
        TestConstants.TEST_IDP_SUB,
        TestConstants.TEST_EMAIL,
        TestConstants.TEST_DISPLAY_NAME);
  }

  private void persistAdminUser() {
    persistUser(
        TestConstants.TEST_ADMIN_ID,
        TestConstants.TEST_IDP_SUB_ADMIN,
        "admin@example.com",
        "Admin User");
  }
}
