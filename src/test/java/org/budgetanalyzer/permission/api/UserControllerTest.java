package org.budgetanalyzer.permission.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.client.SessionGatewayClient;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.domain.UserStatus;
import org.budgetanalyzer.permission.service.UserService;
import org.budgetanalyzer.permission.service.dto.UserActor;
import org.budgetanalyzer.permission.service.dto.UserDeactivationResult;
import org.budgetanalyzer.permission.service.dto.UserDetail;
import org.budgetanalyzer.permission.service.dto.UserWithRoles;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceUnavailableException;
import org.budgetanalyzer.service.security.ClaimsHeaderSecurityConfig;
import org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder;
import org.budgetanalyzer.service.servlet.api.ServletApiExceptionHandler;

@WebMvcTest(UserController.class)
@Import({ClaimsHeaderSecurityConfig.class, ServletApiExceptionHandler.class})
@DisplayName("UserController")
class UserControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UserService userService;
  @MockitoBean private SessionGatewayClient sessionGatewayClient;

  @Nested
  @DisplayName("GET /v1/users")
  class GetUsersTests {

    @Test
    void shouldReturnPagedUsersWhenFilterAndSortAreValid() throws Exception {
      var firstUser = createUser(TestConstants.TEST_USER_ID, TestConstants.TEST_EMAIL);
      var secondUser = createUser("usr_second789", "admin@example.com");
      var pageable = PageRequest.of(1, 5, Sort.by(Sort.Order.asc("email")));
      var page =
          new PageImpl<>(
              List.of(
                  new UserWithRoles(firstUser, List.of(TestConstants.ROLE_USER)),
                  new UserWithRoles(
                      secondUser, List.of(TestConstants.ROLE_ADMIN, TestConstants.ROLE_USER))),
              pageable,
              7);
      when(userService.search(any(), any())).thenReturn(page);

      mockMvc
          .perform(
              get("/v1/users")
                  .queryParam("email", "admin user")
                  .queryParam("status", "ACTIVE")
                  .queryParam("page", "1")
                  .queryParam("size", "5")
                  .queryParam("sort", "email,asc")
                  .with(
                      ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                          .withPermissions(TestConstants.PERM_USERS_READ)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.content[0].id").value(TestConstants.TEST_USER_ID))
          .andExpect(jsonPath("$.content[0].roleIds[0]").value(TestConstants.ROLE_USER))
          .andExpect(jsonPath("$.content[1].roleIds[0]").value(TestConstants.ROLE_ADMIN))
          .andExpect(jsonPath("$.metadata.page").value(1))
          .andExpect(jsonPath("$.metadata.size").value(5))
          .andExpect(jsonPath("$.metadata.totalElements").value(7));

      verify(userService)
          .search(
              argThat(
                  userFilter ->
                      userFilter != null
                          && "admin user".equals(userFilter.email())
                          && userFilter.status() == UserStatus.ACTIVE),
              argThat(
                  requestPageable ->
                      requestPageable != null
                          && requestPageable.getPageNumber() == 1
                          && requestPageable.getPageSize() == 5
                          && Sort.Direction.ASC.equals(
                              requestPageable.getSort().getOrderFor("email").getDirection())));
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

      verify(userService, never()).search(any(), any());
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
      var user = createUser(TestConstants.TEST_USER_ID, TestConstants.TEST_EMAIL);
      var adminUser = createAdminUser();
      user.deactivate(TestConstants.TEST_ADMIN_ID);
      when(userService.getUserDetail(TestConstants.TEST_USER_ID))
          .thenReturn(
              new UserDetail(
                  user,
                  List.of(TestConstants.ROLE_ADMIN, TestConstants.ROLE_USER),
                  UserActor.from(adminUser),
                  null));

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
      var user = createUser(TestConstants.TEST_USER_ID, TestConstants.TEST_EMAIL);
      user.deactivate("usr_missing999");
      when(userService.getUserDetail(TestConstants.TEST_USER_ID))
          .thenReturn(
              new UserDetail(
                  user,
                  List.of(TestConstants.ROLE_USER),
                  new UserActor("usr_missing999", null, null),
                  null));

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
      when(userService.getUserDetail(TestConstants.TEST_USER_ID))
          .thenThrow(
              new ResourceNotFoundException("User not found: " + TestConstants.TEST_USER_ID));

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
      var result =
          new UserDeactivationResult(TestConstants.TEST_USER_ID, UserStatus.DEACTIVATED, 2, true);
      when(userService.deactivateUser(TestConstants.TEST_USER_ID, TestConstants.TEST_ADMIN_ID))
          .thenReturn(result);

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
      when(userService.deactivateUser(TestConstants.TEST_USER_ID, TestConstants.TEST_ADMIN_ID))
          .thenThrow(
              new ResourceNotFoundException("User not found: " + TestConstants.TEST_USER_ID));

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
      when(userService.deactivateUser(TestConstants.TEST_USER_ID, TestConstants.TEST_ADMIN_ID))
          .thenThrow(
              new ServiceUnavailableException(
                  "User "
                      + TestConstants.TEST_USER_ID
                      + " was deactivated but session revocation failed; retry is safe"));

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

  private User createUser(String id, String email) {
    return new User(id, TestConstants.TEST_IDP_SUB, email, TestConstants.TEST_DISPLAY_NAME);
  }

  private User createAdminUser() {
    return new User(
        TestConstants.TEST_ADMIN_ID,
        TestConstants.TEST_IDP_SUB_ADMIN,
        "admin@example.com",
        "Admin User");
  }
}
