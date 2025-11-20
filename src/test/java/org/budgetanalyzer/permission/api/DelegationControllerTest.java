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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.api.request.DelegationRequest;
import org.budgetanalyzer.permission.domain.Delegation;
import org.budgetanalyzer.permission.service.DelegationService;
import org.budgetanalyzer.permission.service.dto.DelegationsSummary;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.security.test.TestSecurityConfig;
import org.budgetanalyzer.service.servlet.api.ServletApiExceptionHandler;

@WebMvcTest(DelegationController.class)
@Import({TestSecurityConfig.class, ServletApiExceptionHandler.class})
@EnableMethodSecurity
@DisplayName("DelegationController")
class DelegationControllerTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;

  @MockitoBean private DelegationService delegationService;

  private Jwt createJwt(String userId) {
    return Jwt.withTokenValue("test-token").header("alg", "RS256").claim("sub", userId).build();
  }

  @Nested
  @DisplayName("GET /v1/delegations")
  class GetDelegationsTests {

    @Test
    @DisplayName("should return delegations for authenticated user")
    void shouldReturnDelegationsForAuthenticatedUser() throws Exception {
      // Arrange
      var givenDelegation = new Delegation();
      givenDelegation.setId(1L);
      givenDelegation.setDelegatorId(TestConstants.TEST_USER_ID);
      givenDelegation.setDelegateeId(TestConstants.TEST_DELEGATEE_ID);
      givenDelegation.setScope("full");

      var summary = new DelegationsSummary(List.of(givenDelegation), List.of());
      when(delegationService.getDelegationsForUser(TestConstants.TEST_USER_ID)).thenReturn(summary);

      var jwt = createJwt(TestConstants.TEST_USER_ID);

      // Act & Assert
      mockMvc
          .perform(get("/v1/delegations").with(jwt().jwt(jwt)))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.given").isArray())
          .andExpect(jsonPath("$.received").isArray())
          .andExpect(jsonPath("$.given.length()").value(1));
    }

    @Test
    @DisplayName("should return 401 when not authenticated")
    void shouldReturnUnauthorizedWhenNotAuthenticated() throws Exception {
      mockMvc.perform(get("/v1/delegations")).andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("POST /v1/delegations")
  class CreateDelegationTests {

    @Test
    @DisplayName("should create delegation successfully")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    void shouldCreateDelegationSuccessfully() throws Exception {
      // Arrange
      var request =
          new DelegationRequest(TestConstants.TEST_DELEGATEE_ID, "full", null, null, null);

      var createdDelegation = new Delegation();
      createdDelegation.setId(1L);
      createdDelegation.setDelegatorId(TestConstants.TEST_USER_ID);
      createdDelegation.setDelegateeId(TestConstants.TEST_DELEGATEE_ID);
      createdDelegation.setScope("full");
      createdDelegation.setValidFrom(Instant.now());

      when(delegationService.createDelegation(
              eq(TestConstants.TEST_USER_ID),
              eq(TestConstants.TEST_DELEGATEE_ID),
              eq("full"),
              any(),
              any(),
              any()))
          .thenReturn(createdDelegation);

      var jwt = createJwt(TestConstants.TEST_USER_ID);

      // Act & Assert
      mockMvc
          .perform(
              post("/v1/delegations")
                  .with(jwt().jwt(jwt))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated())
          .andExpect(header().exists("Location"))
          .andExpect(jsonPath("$.id").value(1))
          .andExpect(jsonPath("$.scope").value("full"));
    }

    @Test
    @DisplayName("should create delegation with expiration")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    void shouldCreateDelegationWithExpiration() throws Exception {
      // Arrange
      var validUntil = Instant.now().plusSeconds(86400);
      var request =
          new DelegationRequest(
              TestConstants.TEST_DELEGATEE_ID,
              "read_only",
              "transaction",
              new String[] {"txn_123"},
              validUntil);

      var createdDelegation = new Delegation();
      createdDelegation.setId(1L);
      createdDelegation.setDelegatorId(TestConstants.TEST_USER_ID);
      createdDelegation.setDelegateeId(TestConstants.TEST_DELEGATEE_ID);
      createdDelegation.setScope("read_only");
      createdDelegation.setValidUntil(validUntil);

      when(delegationService.createDelegation(
              anyString(), anyString(), anyString(), any(), any(), any()))
          .thenReturn(createdDelegation);

      var jwt = createJwt(TestConstants.TEST_USER_ID);

      // Act & Assert
      mockMvc
          .perform(
              post("/v1/delegations")
                  .with(jwt().jwt(jwt))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isCreated())
          .andExpect(jsonPath("$.scope").value("read_only"));
    }

    @Test
    @DisplayName("should return 404 when delegatee not found")
    void shouldReturnNotFoundWhenDelegateeNotFound() throws Exception {
      // Arrange
      var request = new DelegationRequest("nonexistent_user", "full", null, null, null);

      when(delegationService.createDelegation(
              anyString(), anyString(), anyString(), any(), any(), any()))
          .thenThrow(new ResourceNotFoundException("Delegatee not found"));

      var jwt = createJwt(TestConstants.TEST_USER_ID);

      // Act & Assert
      mockMvc
          .perform(
              post("/v1/delegations")
                  .with(jwt().jwt(jwt))
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("should return 403 when not authenticated")
    void shouldReturnForbiddenWhenNotAuthenticated() throws Exception {
      var request =
          new DelegationRequest(TestConstants.TEST_DELEGATEE_ID, "full", null, null, null);

      mockMvc
          .perform(
              post("/v1/delegations")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(objectMapper.writeValueAsString(request)))
          .andExpect(status().isForbidden());
    }
  }

  @Nested
  @DisplayName("DELETE /v1/delegations/{id}")
  class RevokeDelegationTests {

    @Test
    @DisplayName("should revoke delegation successfully")
    void shouldRevokeDelegationSuccessfully() throws Exception {
      // Arrange
      var jwt = createJwt(TestConstants.TEST_USER_ID);

      // Act & Assert
      mockMvc
          .perform(delete("/v1/delegations/{id}", 1L).with(jwt().jwt(jwt)))
          .andExpect(status().isNoContent());

      verify(delegationService).revokeDelegation(eq(1L), anyString());
    }

    @Test
    @DisplayName("should return 404 when delegation not found")
    void shouldReturnNotFoundWhenDelegationNotFound() throws Exception {
      // Arrange
      var jwt = createJwt(TestConstants.TEST_USER_ID);

      doThrow(new ResourceNotFoundException("Delegation not found"))
          .when(delegationService)
          .revokeDelegation(eq(999L), anyString());

      // Act & Assert
      mockMvc
          .perform(delete("/v1/delegations/{id}", 999L).with(jwt().jwt(jwt)))
          .andExpect(status().isNotFound())
          .andExpect(jsonPath("$.type").value("NOT_FOUND"));
    }

    @Test
    @DisplayName("should return 403 when not authenticated")
    void shouldReturnForbiddenWhenNotAuthenticated() throws Exception {
      mockMvc.perform(delete("/v1/delegations/{id}", 1L)).andExpect(status().isForbidden());
    }
  }
}
