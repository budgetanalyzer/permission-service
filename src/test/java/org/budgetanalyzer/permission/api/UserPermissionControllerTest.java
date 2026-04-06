package org.budgetanalyzer.permission.api;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.client.SessionGatewayClient;
import org.budgetanalyzer.permission.domain.UserStatus;
import org.budgetanalyzer.permission.service.UserService;
import org.budgetanalyzer.permission.service.dto.UserDeactivationResult;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceUnavailableException;
import org.budgetanalyzer.service.security.ClaimsHeaderSecurityConfig;
import org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder;
import org.budgetanalyzer.service.servlet.api.ServletApiExceptionHandler;

@WebMvcTest(UserPermissionController.class)
@Import({ClaimsHeaderSecurityConfig.class, ServletApiExceptionHandler.class})
@DisplayName("UserPermissionController")
class UserPermissionControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;
  @MockitoBean private SessionGatewayClient sessionGatewayClient;

  @Nested
  @DisplayName("POST /v1/users/{id}/deactivate")
  class DeactivateUserTests {

    @Test
    @DisplayName("should deactivate user")
    void shouldDeactivateUser() throws Exception {
      // Arrange
      var result =
          new UserDeactivationResult(TestConstants.TEST_USER_ID, UserStatus.DEACTIVATED, 2, true);
      when(userService.deactivateUser(TestConstants.TEST_USER_ID, TestConstants.TEST_ADMIN_ID))
          .thenReturn(result);

      // Act & Assert
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
    @DisplayName("should return 404 when user not found")
    void shouldReturn404WhenUserNotFound() throws Exception {
      // Arrange
      when(userService.deactivateUser(TestConstants.TEST_USER_ID, TestConstants.TEST_ADMIN_ID))
          .thenThrow(
              new ResourceNotFoundException("User not found: " + TestConstants.TEST_USER_ID));

      // Act & Assert
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
    @DisplayName("should return 403 when lacking permission")
    void shouldReturn403WhenLackingPermission() throws Exception {
      mockMvc
          .perform(
              post("/v1/users/{id}/deactivate", TestConstants.TEST_USER_ID)
                  .with(ClaimsHeaderTestBuilder.user(TestConstants.TEST_USER_ID).withPermissions()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 503 when session revocation fails")
    void shouldReturn503WhenSessionRevocationFails() throws Exception {
      // Arrange
      when(userService.deactivateUser(TestConstants.TEST_USER_ID, TestConstants.TEST_ADMIN_ID))
          .thenThrow(
              new ServiceUnavailableException(
                  "User "
                      + TestConstants.TEST_USER_ID
                      + " was deactivated but session revocation failed; retry is safe"));

      // Act & Assert
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
}
