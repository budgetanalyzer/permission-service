package org.budgetanalyzer.permission.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import org.budgetanalyzer.permission.api.request.UserRoleAssignmentRequest;
import org.budgetanalyzer.permission.domain.Role;
import org.budgetanalyzer.permission.service.PermissionService;
import org.budgetanalyzer.permission.service.dto.EffectivePermissions;
import org.budgetanalyzer.permission.service.exception.DuplicateRoleAssignmentException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.security.ClaimsHeaderSecurityConfig;
import org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder;
import org.budgetanalyzer.service.servlet.api.ServletApiExceptionHandler;

@WebMvcTest(UserPermissionController.class)
@Import({ClaimsHeaderSecurityConfig.class, ServletApiExceptionHandler.class})
@DisplayName("UserPermissionController")
class UserPermissionControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private PermissionService permissionService;

  @Nested
  @DisplayName("GET /v1/users/me/permissions")
  class GetCurrentUserPermissionsTests {

    @Test
    @DisplayName("should return permissions for authenticated user")
    void shouldReturnPermissionsForAuthenticatedUser() throws Exception {
      // Arrange
      var permissions =
          new EffectivePermissions(
              Set.of("USER"), Set.of("transactions:read", "transactions:write"));
      when(permissionService.getEffectivePermissions(TestConstants.TEST_USER_ID))
          .thenReturn(permissions);

      // Act & Assert
      mockMvc
          .perform(
              get("/v1/users/me/permissions")
                  .with(ClaimsHeaderTestBuilder.user(TestConstants.TEST_USER_ID)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.permissions").isArray());
    }

    @Test
    @DisplayName("should return 401 when not authenticated")
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
      mockMvc.perform(get("/v1/users/me/permissions")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("GET /v1/users/{id}/permissions")
  class GetUserPermissionsTests {

    @Test
    @DisplayName("should return permissions when user has users:read permission")
    void shouldReturnPermissionsWithUsersReadPermission() throws Exception {
      // Arrange
      var permissions = new EffectivePermissions(Set.of("USER"), Set.of("transactions:read"));
      when(permissionService.getEffectivePermissions(TestConstants.TEST_USER_ID))
          .thenReturn(permissions);

      // Act & Assert
      mockMvc
          .perform(
              get("/v1/users/{id}/permissions", TestConstants.TEST_USER_ID)
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_USERS_READ)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.permissions").isArray());
    }

    @Test
    @DisplayName("should return 403 when user lacks users:read permission")
    void shouldReturnForbiddenWhenLackingPermission() throws Exception {
      mockMvc
          .perform(
              get("/v1/users/{id}/permissions", TestConstants.TEST_USER_ID)
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID).withPermissions()))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("GET /v1/users/{id}/roles")
  class GetUserRolesTests {

    @Test
    @DisplayName("should return roles when user has users:read permission")
    void shouldReturnRolesWithUsersReadPermission() throws Exception {
      // Arrange
      var role = new Role();
      role.setId("USER");
      role.setName("User");
      when(permissionService.getUserRoles(TestConstants.TEST_USER_ID)).thenReturn(List.of(role));

      // Act & Assert
      mockMvc
          .perform(
              get("/v1/users/{id}/roles", TestConstants.TEST_USER_ID)
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_USERS_READ)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$[0].id").value("USER"));
    }

    @Test
    @DisplayName("should allow user to view own roles")
    void shouldAllowUserToViewOwnRoles() throws Exception {
      // Arrange
      var role = new Role();
      role.setId("USER");
      role.setName("User");
      when(permissionService.getUserRoles(TestConstants.TEST_USER_ID)).thenReturn(List.of(role));

      // Act & Assert
      mockMvc
          .perform(
              get("/v1/users/{id}/roles", TestConstants.TEST_USER_ID)
                  .with(ClaimsHeaderTestBuilder.user(TestConstants.TEST_USER_ID).withPermissions()))
          .andExpect(status().isOk());
    }
  }

  @Nested
  @DisplayName("POST /v1/users/{id}/roles")
  class AssignRoleTests {

    @Test
    @DisplayName("should assign role successfully with roles:write permission")
    void shouldAssignRoleSuccessfully() throws Exception {
      // Arrange
      var request = new UserRoleAssignmentRequest("USER");

      // Act & Assert
      mockMvc
          .perform(
              post("/v1/users/{id}/roles", TestConstants.TEST_USER_ID)
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_ROLES_WRITE))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isNoContent());

      verify(permissionService).assignRole(TestConstants.TEST_USER_ID, "USER");
    }

    @Test
    @DisplayName("should return 422 when role already assigned")
    void shouldReturnUnprocessableEntityWhenRoleAlreadyAssigned() throws Exception {
      // Arrange
      var request = new UserRoleAssignmentRequest("USER");

      doThrow(new DuplicateRoleAssignmentException(TestConstants.TEST_USER_ID, "USER"))
          .when(permissionService)
          .assignRole(any(), eq("USER"));

      // Act & Assert
      mockMvc
          .perform(
              post("/v1/users/{id}/roles", TestConstants.TEST_USER_ID)
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_ROLES_WRITE))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @DisplayName("should return 400 when roleId is blank")
    void shouldReturnBadRequestWhenRoleIdIsBlank() throws Exception {
      // Arrange
      var request = new UserRoleAssignmentRequest("");

      // Act & Assert
      mockMvc
          .perform(
              post("/v1/users/{id}/roles", TestConstants.TEST_USER_ID)
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_ROLES_WRITE))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"));
    }

    @Test
    @DisplayName("should return 403 when user lacks permission")
    void shouldReturnForbiddenWhenUserLacksPermission() throws Exception {
      var request = new UserRoleAssignmentRequest("USER");

      mockMvc
          .perform(
              post("/v1/users/{id}/roles", TestConstants.TEST_USER_ID)
                  .with(ClaimsHeaderTestBuilder.user(TestConstants.TEST_USER_ID).withPermissions())
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("DELETE /v1/users/{id}/roles/{roleId}")
  class RevokeRoleTests {

    @Test
    @DisplayName("should revoke role successfully with roles:write permission")
    void shouldRevokeRoleSuccessfully() throws Exception {
      // Act & Assert
      mockMvc
          .perform(
              delete("/v1/users/{id}/roles/{roleId}", TestConstants.TEST_USER_ID, "USER")
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_ROLES_WRITE)))
          .andExpect(status().isNoContent());

      verify(permissionService).revokeRole(TestConstants.TEST_USER_ID, "USER");
    }

    @Test
    @DisplayName("should return 403 when user lacks roles:write permission")
    void shouldReturnForbiddenWhenUserLacksPermission() throws Exception {
      mockMvc
          .perform(
              delete("/v1/users/{id}/roles/{roleId}", TestConstants.TEST_USER_ID, "USER")
                  .with(ClaimsHeaderTestBuilder.user(TestConstants.TEST_USER_ID).withPermissions()))
          .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("should return 404 when role assignment not found")
    void shouldReturnNotFoundWhenAssignmentNotFound() throws Exception {
      // Arrange
      doThrow(new ResourceNotFoundException("Role assignment not found"))
          .when(permissionService)
          .revokeRole(any(), eq("NONEXISTENT"));

      // Act & Assert
      mockMvc
          .perform(
              delete("/v1/users/{id}/roles/{roleId}", TestConstants.TEST_USER_ID, "NONEXISTENT")
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_ROLES_WRITE)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("NOT_FOUND"));
    }
  }
}
