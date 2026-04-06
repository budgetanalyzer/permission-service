package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.repository.UserRoleRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionService")
class PermissionServiceTest {

  @Mock private UserRoleRepository userRoleRepository;

  private PermissionService permissionService;

  @BeforeEach
  void setUp() {
    permissionService = new PermissionService(userRoleRepository);
  }

  @Nested
  @DisplayName("getEffectivePermissions")
  class GetEffectivePermissionsTests {

    @Test
    @DisplayName("should return roles and permissions for user")
    void shouldReturnRolesAndPermissionsForUser() {
      // Arrange
      when(userRoleRepository.findRoleIdsByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(Set.of("USER"));
      when(userRoleRepository.findPermissionIdsByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(Set.of("transactions:read", "transactions:write"));

      // Act
      var result = permissionService.getEffectivePermissions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result.roles()).containsExactly("USER");
      assertThat(result.permissions())
          .containsExactlyInAnyOrder("transactions:read", "transactions:write");
    }

    @Test
    @DisplayName("should return empty when user has no permissions")
    void shouldReturnEmptyWhenNoPermissions() {
      // Arrange
      when(userRoleRepository.findRoleIdsByUserId(TestConstants.TEST_USER_ID)).thenReturn(Set.of());
      when(userRoleRepository.findPermissionIdsByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(Set.of());

      // Act
      var result = permissionService.getEffectivePermissions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result.roles()).isEmpty();
      assertThat(result.permissions()).isEmpty();
    }
  }
}
