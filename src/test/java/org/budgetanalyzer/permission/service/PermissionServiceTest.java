package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.domain.Role;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.domain.UserRole;
import org.budgetanalyzer.permission.repository.RoleRepository;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;
import org.budgetanalyzer.permission.service.exception.DuplicateRoleAssignmentException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionService")
class PermissionServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private UserRoleRepository userRoleRepository;
  @Mock private RoleRepository roleRepository;

  private PermissionService permissionService;

  @BeforeEach
  void setUp() {
    permissionService = new PermissionService(userRepository, userRoleRepository, roleRepository);
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

  @Nested
  @DisplayName("assignRole")
  class AssignRoleTests {

    @Test
    @DisplayName("should assign role successfully")
    void shouldAssignRoleSuccessfully() {
      // Arrange
      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      when(userRepository.findByIdAndDeletedFalse(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));

      var role = new Role();
      role.setId("USER");
      when(roleRepository.findByIdAndDeletedFalse("USER")).thenReturn(Optional.of(role));

      when(userRoleRepository.findByUserIdAndRoleId(TestConstants.TEST_USER_ID, "USER"))
          .thenReturn(Optional.empty());

      // Act
      permissionService.assignRole(TestConstants.TEST_USER_ID, "USER");

      // Assert
      var captor = ArgumentCaptor.forClass(UserRole.class);
      verify(userRoleRepository).save(captor.capture());
      assertThat(captor.getValue().getUserId()).isEqualTo(TestConstants.TEST_USER_ID);
      assertThat(captor.getValue().getRoleId()).isEqualTo("USER");
    }

    @Test
    @DisplayName("should throw DuplicateRoleAssignmentException when role already assigned")
    void shouldThrowWhenRoleAlreadyAssigned() {
      // Arrange
      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      when(userRepository.findByIdAndDeletedFalse(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));

      var role = new Role();
      role.setId("USER");
      when(roleRepository.findByIdAndDeletedFalse("USER")).thenReturn(Optional.of(role));

      when(userRoleRepository.findByUserIdAndRoleId(TestConstants.TEST_USER_ID, "USER"))
          .thenReturn(Optional.of(new UserRole()));

      // Act & Assert
      assertThatThrownBy(() -> permissionService.assignRole(TestConstants.TEST_USER_ID, "USER"))
          .isInstanceOf(DuplicateRoleAssignmentException.class);

      verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when user not found")
    void shouldThrowWhenUserNotFound() {
      // Arrange
      when(userRepository.findByIdAndDeletedFalse(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(() -> permissionService.assignRole(TestConstants.TEST_USER_ID, "USER"))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("User not found");
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when role not found")
    void shouldThrowWhenRoleNotFound() {
      // Arrange
      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      when(userRepository.findByIdAndDeletedFalse(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));

      when(roleRepository.findByIdAndDeletedFalse("NONEXISTENT")).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(
              () -> permissionService.assignRole(TestConstants.TEST_USER_ID, "NONEXISTENT"))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Role not found");
    }
  }

  @Nested
  @DisplayName("revokeRole")
  class RevokeRoleTests {

    @Test
    @DisplayName("should revoke role successfully via hard delete")
    void shouldRevokeRoleSuccessfully() {
      // Arrange
      var userRole = new UserRole();
      userRole.setUserId(TestConstants.TEST_USER_ID);
      userRole.setRoleId("USER");
      when(userRoleRepository.findByUserIdAndRoleId(TestConstants.TEST_USER_ID, "USER"))
          .thenReturn(Optional.of(userRole));

      // Act
      permissionService.revokeRole(TestConstants.TEST_USER_ID, "USER");

      // Assert
      verify(userRoleRepository).deleteByUserIdAndRoleId(TestConstants.TEST_USER_ID, "USER");
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when role assignment not found")
    void shouldThrowWhenAssignmentNotFound() {
      // Arrange
      when(userRoleRepository.findByUserIdAndRoleId(TestConstants.TEST_USER_ID, "USER"))
          .thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(() -> permissionService.revokeRole(TestConstants.TEST_USER_ID, "USER"))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("getUserRoles")
  class GetUserRolesTests {

    @Test
    @DisplayName("should return all roles for user")
    void shouldReturnRolesForUser() {
      // Arrange
      var userRole1 = new UserRole();
      userRole1.setRoleId("USER");
      var userRole2 = new UserRole();
      userRole2.setRoleId("ADMIN");

      when(userRoleRepository.findByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(List.of(userRole1, userRole2));

      var role1 = new Role();
      role1.setId("USER");
      role1.setName("User");
      var role2 = new Role();
      role2.setId("ADMIN");
      role2.setName("Administrator");

      when(roleRepository.findByIdAndDeletedFalse("USER")).thenReturn(Optional.of(role1));
      when(roleRepository.findByIdAndDeletedFalse("ADMIN")).thenReturn(Optional.of(role2));

      // Act
      var result = permissionService.getUserRoles(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result).hasSize(2);
      assertThat(result).extracting(Role::getId).containsExactlyInAnyOrder("USER", "ADMIN");
    }

    @Test
    @DisplayName("should return empty list when user has no roles")
    void shouldReturnEmptyWhenNoRoles() {
      // Arrange
      when(userRoleRepository.findByUserId(TestConstants.TEST_USER_ID)).thenReturn(List.of());

      // Act
      var result = permissionService.getUserRoles(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result).isEmpty();
    }
  }
}
