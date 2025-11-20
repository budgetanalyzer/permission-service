package org.budgetanalyzer.permission.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.api.request.RoleRequest;
import org.budgetanalyzer.permission.domain.Role;
import org.budgetanalyzer.permission.service.RoleService;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.security.test.TestSecurityConfig;
import org.budgetanalyzer.service.servlet.api.ServletApiExceptionHandler;

@WebMvcTest(RoleController.class)
@Import({TestSecurityConfig.class, ServletApiExceptionHandler.class})
@EnableMethodSecurity
@DisplayName("RoleController")
class RoleControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private RoleService roleService;

  private Jwt createJwtWithPermissions(String userId, String... permissions) {
    return Jwt.withTokenValue("test-token")
        .header("alg", "RS256")
        .claim("sub", userId)
        .claim("permissions", List.of(permissions))
        .build();
  }

  @Nested
  @DisplayName("GET /v1/roles")
  class GetAllRolesTests {

    @Test
    @DisplayName("should return all roles when user has roles:read permission")
    void shouldReturnAllRolesWithPermission() throws Exception {
      // Arrange
      var role1 = new Role();
      role1.setId("USER");
      role1.setName("User");
      var role2 = new Role();
      role2.setId("MANAGER");
      role2.setName("Manager");

      when(roleService.getAllRoles()).thenReturn(List.of(role1, role2));

      var jwt =
          createJwtWithPermissions(TestConstants.TEST_ADMIN_ID, TestConstants.PERM_ROLES_READ);

      // Act & Assert
      mockMvc
          .perform(
              get("/v1/roles")
                  .with(
                      jwt()
                          .jwt(jwt)
                          .authorities(new SimpleGrantedAuthority(TestConstants.PERM_ROLES_READ))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$").isArray())
          .andExpect(jsonPath("$.length()").value(2))
          .andExpect(jsonPath("$[0].id").value("USER"));
    }

    @Test
    @DisplayName("should return 403 when user lacks roles:read permission")
    void shouldReturnForbiddenWhenLackingPermission() throws Exception {
      var jwt = createJwtWithPermissions(TestConstants.TEST_USER_ID);

      mockMvc.perform(get("/v1/roles").with(jwt().jwt(jwt))).andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("GET /v1/roles/{id}")
  class GetRoleTests {

    @Test
    @DisplayName("should return role when found")
    void shouldReturnRoleWhenFound() throws Exception {
      // Arrange
      var role = new Role();
      role.setId("MANAGER");
      role.setName("Manager");
      role.setDescription("Manages team resources");

      when(roleService.getRole("MANAGER")).thenReturn(role);

      var jwt =
          createJwtWithPermissions(TestConstants.TEST_ADMIN_ID, TestConstants.PERM_ROLES_READ);

      // Act & Assert
      mockMvc
          .perform(
              get("/v1/roles/{id}", "MANAGER")
                  .with(
                      jwt()
                          .jwt(jwt)
                          .authorities(new SimpleGrantedAuthority(TestConstants.PERM_ROLES_READ))))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.id").value("MANAGER"))
          .andExpect(jsonPath("$.name").value("Manager"));
    }

    @Test
    @DisplayName("should return 404 when role not found")
    void shouldReturnNotFoundWhenRoleNotFound() throws Exception {
      // Arrange
      when(roleService.getRole("NONEXISTENT"))
          .thenThrow(new ResourceNotFoundException("Role not found"));

      var jwt =
          createJwtWithPermissions(TestConstants.TEST_ADMIN_ID, TestConstants.PERM_ROLES_READ);

      // Act & Assert
      mockMvc
          .perform(
              get("/v1/roles/{id}", "NONEXISTENT")
                  .with(
                      jwt()
                          .jwt(jwt)
                          .authorities(new SimpleGrantedAuthority(TestConstants.PERM_ROLES_READ))))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("NOT_FOUND"));
    }
  }

  @Nested
  @DisplayName("POST /v1/roles")
  class CreateRoleTests {

    @Test
    @DisplayName("should create role successfully with roles:write permission")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    void shouldCreateRoleSuccessfully() throws Exception {
      // Arrange
      var request = new RoleRequest("Project Manager", "Manages project resources", null);

      var createdRole = new Role();
      createdRole.setId("role_abc123");
      createdRole.setName("Project Manager");
      createdRole.setDescription("Manages project resources");

      when(roleService.createRole(anyString(), anyString(), any())).thenReturn(createdRole);

      var jwt =
          createJwtWithPermissions(TestConstants.TEST_ADMIN_ID, TestConstants.PERM_ROLES_WRITE);

      // Act & Assert
      mockMvc
          .perform(
              post("/v1/roles")
                  .with(
                      jwt()
                          .jwt(jwt)
                          .authorities(new SimpleGrantedAuthority(TestConstants.PERM_ROLES_WRITE)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated())
          .andExpect(header().exists("Location"))
          .andExpect(jsonPath("$.id").value("role_abc123"))
          .andExpect(jsonPath("$.name").value("Project Manager"));

      verify(roleService).createRole("Project Manager", "Manages project resources", null);
    }

    @Test
    @DisplayName("should return 400 when name is blank")
    void shouldReturnBadRequestWhenNameIsBlank() throws Exception {
      // Arrange
      var request = new RoleRequest("", "description", null);
      var jwt =
          createJwtWithPermissions(TestConstants.TEST_ADMIN_ID, TestConstants.PERM_ROLES_WRITE);

      // Act & Assert
      mockMvc
          .perform(
              post("/v1/roles")
                  .with(
                      jwt()
                          .jwt(jwt)
                          .authorities(new SimpleGrantedAuthority(TestConstants.PERM_ROLES_WRITE)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.type").value("VALIDATION_ERROR"))
          .andExpect(jsonPath("$.fieldErrors").isArray())
          .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    @DisplayName("should return 403 when user lacks roles:write permission")
    void shouldReturnForbiddenWhenLackingWritePermission() throws Exception {
      var request = new RoleRequest("Test Role", "description", null);
      var jwt = createJwtWithPermissions(TestConstants.TEST_USER_ID);

      mockMvc
          .perform(
              post("/v1/roles")
                  .with(jwt().jwt(jwt))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("PUT /v1/roles/{id}")
  class UpdateRoleTests {

    @Test
    @DisplayName("should update role successfully")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    void shouldUpdateRoleSuccessfully() throws Exception {
      // Arrange
      var request = new RoleRequest("Updated Name", "Updated description", null);

      var updatedRole = new Role();
      updatedRole.setId("MANAGER");
      updatedRole.setName("Updated Name");
      updatedRole.setDescription("Updated description");

      when(roleService.updateRole(eq("MANAGER"), anyString(), anyString(), any()))
          .thenReturn(updatedRole);

      var jwt =
          createJwtWithPermissions(TestConstants.TEST_ADMIN_ID, TestConstants.PERM_ROLES_WRITE);

      // Act & Assert
      mockMvc
          .perform(
              put("/v1/roles/{id}", "MANAGER")
                  .with(
                      jwt()
                          .jwt(jwt)
                          .authorities(new SimpleGrantedAuthority(TestConstants.PERM_ROLES_WRITE)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.name").value("Updated Name"));
    }

    @Test
    @DisplayName("should return 404 when role not found")
    void shouldReturnNotFoundWhenUpdatingNonexistentRole() throws Exception {
      // Arrange
      var request = new RoleRequest("Name", "description", null);

      when(roleService.updateRole(eq("NONEXISTENT"), anyString(), anyString(), any()))
          .thenThrow(new ResourceNotFoundException("Role not found"));

      var jwt =
          createJwtWithPermissions(TestConstants.TEST_ADMIN_ID, TestConstants.PERM_ROLES_WRITE);

      // Act & Assert
      mockMvc
          .perform(
              put("/v1/roles/{id}", "NONEXISTENT")
                  .with(
                      jwt()
                          .jwt(jwt)
                          .authorities(new SimpleGrantedAuthority(TestConstants.PERM_ROLES_WRITE)))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isNotFound());
    }
  }

  @Nested
  @DisplayName("DELETE /v1/roles/{id}")
  class DeleteRoleTests {

    @Test
    @DisplayName("should delete role successfully with roles:delete permission")
    void shouldDeleteRoleSuccessfully() throws Exception {
      // Arrange
      var jwt =
          createJwtWithPermissions(TestConstants.TEST_ADMIN_ID, TestConstants.PERM_ROLES_DELETE);

      // Act & Assert
      mockMvc
          .perform(
              delete("/v1/roles/{id}", "MANAGER")
                  .with(
                      jwt()
                          .jwt(jwt)
                          .authorities(
                              new SimpleGrantedAuthority(TestConstants.PERM_ROLES_DELETE))))
          .andExpect(status().isNoContent());

      verify(roleService).deleteRole(eq("MANAGER"), anyString());
    }

    @Test
    @DisplayName("should return 404 when role not found")
    void shouldReturnNotFoundWhenDeletingNonexistentRole() throws Exception {
      // Arrange
      var jwt =
          createJwtWithPermissions(TestConstants.TEST_ADMIN_ID, TestConstants.PERM_ROLES_DELETE);

      doThrow(new ResourceNotFoundException("Role not found"))
          .when(roleService)
          .deleteRole(eq("NONEXISTENT"), anyString());

      // Act & Assert
      mockMvc
          .perform(
              delete("/v1/roles/{id}", "NONEXISTENT")
                  .with(
                      jwt()
                          .jwt(jwt)
                          .authorities(
                              new SimpleGrantedAuthority(TestConstants.PERM_ROLES_DELETE))))
          .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("should return 403 when user lacks roles:delete permission")
    void shouldReturnForbiddenWhenLackingDeletePermission() throws Exception {
      var jwt = createJwtWithPermissions(TestConstants.TEST_USER_ID);

      mockMvc
          .perform(delete("/v1/roles/{id}", "MANAGER").with(jwt().jwt(jwt)))
          .andExpect(status().isForbidden());
    }
  }
}
