package org.budgetanalyzer.permission.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.client.SessionGatewayClient;
import org.budgetanalyzer.permission.config.PermissionServiceSecurityConfig;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.service.PermissionService;
import org.budgetanalyzer.permission.service.UserService;
import org.budgetanalyzer.permission.service.UserSyncService;
import org.budgetanalyzer.permission.service.dto.EffectivePermissions;
import org.budgetanalyzer.permission.service.dto.UserDeactivationResult;
import org.budgetanalyzer.permission.service.exception.UserDeactivatedException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.security.ClaimsHeaderSecurityConfig;
import org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder;
import org.budgetanalyzer.service.servlet.api.ServletApiExceptionHandler;

@WebMvcTest(InternalPermissionController.class)
@Import({
  PermissionServiceSecurityConfig.class,
  ClaimsHeaderSecurityConfig.class,
  ServletApiExceptionHandler.class
})
@DisplayName("InternalPermissionController")
class InternalPermissionControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private UserSyncService userSyncService;
  @MockitoBean private PermissionService permissionService;
  @MockitoBean private UserService userService;
  @MockitoBean private SessionGatewayClient sessionGatewayClient;

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
    @DisplayName("should allow access without claims headers for the gateway integration path")
    void shouldAllowAccessWithoutClaimsHeadersForGatewayIntegrationPath() throws Exception {
      // Arrange - this narrow internal path is service-owned and orchestration-restricted
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

      // Act & Assert - no .with(ClaimsHeaderTestBuilder...) needed for this path exception
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

    @Test
    @DisplayName("should return 422 when user is deactivated")
    void shouldReturn422WhenUserIsDeactivated() throws Exception {
      // Arrange
      when(userSyncService.syncUser(
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME))
          .thenThrow(new UserDeactivatedException(TestConstants.TEST_IDP_SUB));

      // Act & Assert
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

  @Nested
  @DisplayName("POST /internal/v1/users/{userId}/deactivate")
  class DeactivateUserTests {

    @Test
    @DisplayName("should deactivate user")
    void shouldDeactivateUser() throws Exception {
      // Arrange
      var result = new UserDeactivationResult(TestConstants.TEST_USER_ID, "DEACTIVATED", 2, true);
      when(userService.deactivateUser(
              TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY))
          .thenReturn(result);

      // Act & Assert
      mockMvc
          .perform(
              post("/internal/v1/users/{userId}/deactivate", TestConstants.TEST_USER_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new org.budgetanalyzer.permission.api.request.UserDeactivationRequest(
                              TestConstants.TEST_DEACTIVATED_BY))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.userId").value(TestConstants.TEST_USER_ID))
          .andExpect(jsonPath("$.status").value("DEACTIVATED"))
          .andExpect(jsonPath("$.rolesRemoved").value(2))
          .andExpect(jsonPath("$.sessionsRevoked").value(true));
    }

    @Test
    @DisplayName("should return 404 when user not found")
    void shouldReturn404WhenUserNotFound() throws Exception {
      // Arrange
      when(userService.deactivateUser(
              TestConstants.TEST_USER_ID, TestConstants.TEST_DEACTIVATED_BY))
          .thenThrow(
              new ResourceNotFoundException("User not found: " + TestConstants.TEST_USER_ID));

      // Act & Assert
      mockMvc
          .perform(
              post("/internal/v1/users/{userId}/deactivate", TestConstants.TEST_USER_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(
                      objectMapper.writeValueAsString(
                          new org.budgetanalyzer.permission.api.request.UserDeactivationRequest(
                              TestConstants.TEST_DEACTIVATED_BY))))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("should return 400 when deactivatedBy is blank")
    void shouldReturn400WhenDeactivatedByIsBlank() throws Exception {
      // Act & Assert
      mockMvc
          .perform(
              post("/internal/v1/users/{userId}/deactivate", TestConstants.TEST_USER_ID)
                  .contentType(MediaType.APPLICATION_JSON)
                  .content("{\"deactivatedBy\": \"\"}"))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
    }
  }
}
