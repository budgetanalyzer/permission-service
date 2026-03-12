package org.budgetanalyzer.permission.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.service.PermissionService;
import org.budgetanalyzer.permission.service.UserSyncService;
import org.budgetanalyzer.permission.service.dto.EffectivePermissions;
import org.budgetanalyzer.service.security.ClaimsHeaderSecurityConfig;
import org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder;
import org.budgetanalyzer.service.servlet.api.ServletApiExceptionHandler;

@WebMvcTest(InternalPermissionController.class)
@Import({ClaimsHeaderSecurityConfig.class, ServletApiExceptionHandler.class})
@DisplayName("InternalPermissionController")
class InternalPermissionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserSyncService userSyncService;
  @MockitoBean private PermissionService permissionService;

  @Nested
  @DisplayName("GET /internal/v1/users/{idpSub}/permissions")
  class GetUserPermissionsTests {

    @Test
    @DisplayName("should create user on first login and return permissions")
    void shouldCreateUserAndReturnPermissions() throws Exception {
      // Arrange
      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      user.setIdpSub(TestConstants.TEST_IDP_SUB);

      when(userSyncService.syncUser(
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME))
          .thenReturn(user);

      var effective =
          new EffectivePermissions(
              Set.of("USER"), Set.of("transactions:read", "transactions:write"));
      when(permissionService.getEffectivePermissions(TestConstants.TEST_USER_ID))
          .thenReturn(effective);

      // Act & Assert
      mockMvc
          .perform(
              get("/internal/v1/users/{idpSub}/permissions", TestConstants.TEST_IDP_SUB)
                  .param("email", TestConstants.TEST_EMAIL)
                  .param("displayName", TestConstants.TEST_DISPLAY_NAME)
                  .with(ClaimsHeaderTestBuilder.user(TestConstants.TEST_USER_ID)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(TestConstants.TEST_USER_ID))
          .andExpect(jsonPath("$.roles").isArray())
          .andExpect(jsonPath("$.roles.length()").value(1))
          .andExpect(jsonPath("$.roles", org.hamcrest.Matchers.hasItem("USER")))
          .andExpect(jsonPath("$.permissions").isArray());
    }

    @Test
    @DisplayName("should return existing user permissions on subsequent calls")
    void shouldReturnExistingUserPermissions() throws Exception {
      // Arrange
      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      user.setIdpSub(TestConstants.TEST_IDP_SUB);

      when(userSyncService.syncUser(
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME))
          .thenReturn(user);

      var effective =
          new EffectivePermissions(
              Set.of("USER", "ADMIN"),
              Set.of("transactions:read", "transactions:write", "users:read"));
      when(permissionService.getEffectivePermissions(TestConstants.TEST_USER_ID))
          .thenReturn(effective);

      // Act & Assert
      mockMvc
          .perform(
              get("/internal/v1/users/{idpSub}/permissions", TestConstants.TEST_IDP_SUB)
                  .param("email", TestConstants.TEST_EMAIL)
                  .param("displayName", TestConstants.TEST_DISPLAY_NAME)
                  .with(ClaimsHeaderTestBuilder.user(TestConstants.TEST_USER_ID)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(TestConstants.TEST_USER_ID))
          .andExpect(jsonPath("$.roles.length()").value(2))
          .andExpect(jsonPath("$.permissions.length()").value(3));
    }

    @Test
    @DisplayName("should allow unauthenticated access to internal endpoint")
    void shouldAllowUnauthenticatedAccessToInternalEndpoint() throws Exception {
      // Arrange - internal endpoints are accessible without auth headers
      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      user.setIdpSub(TestConstants.TEST_IDP_SUB);

      when(userSyncService.syncUser(
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME))
          .thenReturn(user);

      var effective =
          new EffectivePermissions(
              Set.of("USER"), Set.of("transactions:read", "transactions:write"));
      when(permissionService.getEffectivePermissions(TestConstants.TEST_USER_ID))
          .thenReturn(effective);

      // Act & Assert - no .with(ClaimsHeaderTestBuilder...) needed
      mockMvc
          .perform(
              get("/internal/v1/users/{idpSub}/permissions", TestConstants.TEST_IDP_SUB)
                  .param("email", TestConstants.TEST_EMAIL)
                  .param("displayName", TestConstants.TEST_DISPLAY_NAME))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(TestConstants.TEST_USER_ID))
          .andExpect(jsonPath("$.roles").isArray())
          .andExpect(jsonPath("$.permissions").isArray());
    }
  }
}
