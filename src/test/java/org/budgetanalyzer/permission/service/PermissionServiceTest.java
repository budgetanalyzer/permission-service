package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
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
import org.budgetanalyzer.permission.domain.ResourcePermission;
import org.budgetanalyzer.permission.domain.Role;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.domain.UserRole;
import org.budgetanalyzer.permission.event.PermissionChangeEvent;
import org.budgetanalyzer.permission.repository.DelegationRepository;
import org.budgetanalyzer.permission.repository.ResourcePermissionRepository;
import org.budgetanalyzer.permission.repository.RolePermissionRepository;
import org.budgetanalyzer.permission.repository.RoleRepository;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;
import org.budgetanalyzer.permission.service.exception.DuplicateRoleAssignmentException;
import org.budgetanalyzer.permission.service.exception.PermissionDeniedException;
import org.budgetanalyzer.permission.service.exception.ProtectedRoleException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("PermissionService")
class PermissionServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private UserRoleRepository userRoleRepository;
  @Mock private RoleRepository roleRepository;
  @Mock private RolePermissionRepository rolePermissionRepository;
  @Mock private ResourcePermissionRepository resourcePermissionRepository;
  @Mock private DelegationRepository delegationRepository;
  @Mock private AuditService auditService;
  @Mock private PermissionCacheService permissionCacheService;

  private PermissionService permissionService;

  @BeforeEach
  void setUp() {
    permissionService =
        new PermissionService(
            userRepository,
            userRoleRepository,
            roleRepository,
            rolePermissionRepository,
            resourcePermissionRepository,
            delegationRepository,
            auditService,
            permissionCacheService);
  }

  @Nested
  @DisplayName("getEffectivePermissions")
  class GetEffectivePermissionsTests {

    @Test
    @DisplayName("should combine permissions from roles and resource permissions")
    void shouldCombinePermissionsFromAllSources() {
      // Arrange
      when(userRoleRepository.findActivePermissionIdsByUserId(
              eq(TestConstants.TEST_USER_ID), any()))
          .thenReturn(Set.of("transactions:read", "transactions:write"));

      var resourcePerm = new ResourcePermission();
      resourcePerm.setPermission("budget:read");
      when(resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_USER_ID))
          .thenReturn(List.of(resourcePerm));

      when(delegationRepository.findActiveDelegationsForUser(eq(TestConstants.TEST_USER_ID), any()))
          .thenReturn(List.of());

      // Act
      var result = permissionService.getEffectivePermissions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result.getAllPermissionIds())
          .containsExactlyInAnyOrder("transactions:read", "transactions:write", "budget:read");
    }

    @Test
    @DisplayName("should return empty when user has no permissions")
    void shouldReturnEmptyWhenNoPermissions() {
      // Arrange
      when(userRoleRepository.findActivePermissionIdsByUserId(
              eq(TestConstants.TEST_USER_ID), any()))
          .thenReturn(Set.of());
      when(resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_USER_ID))
          .thenReturn(List.of());
      when(delegationRepository.findActiveDelegationsForUser(eq(TestConstants.TEST_USER_ID), any()))
          .thenReturn(List.of());

      // Act
      var result = permissionService.getEffectivePermissions(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result.getAllPermissionIds()).isEmpty();
    }
  }

  @Nested
  @DisplayName("assignRole")
  class AssignRoleTests {

    @Test
    @DisplayName("should throw ProtectedRoleException when assigning SYSTEM_ADMIN")
    void assignRoleShouldThrowWhenAttemptingToAssignSystemAdmin() {
      // Act & Assert
      assertThatThrownBy(
              () ->
                  permissionService.assignRole(
                      TestConstants.TEST_USER_ID, "SYSTEM_ADMIN", TestConstants.TEST_ADMIN_ID))
          .isInstanceOf(ProtectedRoleException.class)
          .hasMessageContaining("SYSTEM_ADMIN role cannot be assigned via API");

      // Verify no assignment was made
      verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName(
        "should throw PermissionDeniedException when granter lacks elevated permission for MANAGER")
    void assignRoleShouldThrowWhenGranterLacksElevatedPermission() {
      // Arrange - granter only has basic assign permission
      when(userRoleRepository.findActivePermissionIdsByUserId(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(Set.of(TestConstants.PERM_ASSIGN_BASIC));
      when(resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_ADMIN_ID))
          .thenReturn(List.of());
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(List.of());

      // Act & Assert
      assertThatThrownBy(
              () ->
                  permissionService.assignRole(
                      TestConstants.TEST_USER_ID, "MANAGER", TestConstants.TEST_ADMIN_ID))
          .isInstanceOf(PermissionDeniedException.class)
          .hasMessageContaining("user-roles:assign-elevated");
    }

    @Test
    @DisplayName(
        "should throw PermissionDeniedException when granter lacks basic permission for USER role")
    void assignRoleShouldThrowWhenGranterLacksBasicPermission() {
      // Arrange - granter has no assign permissions
      when(userRoleRepository.findActivePermissionIdsByUserId(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(Set.of());
      when(resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_ADMIN_ID))
          .thenReturn(List.of());
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(List.of());

      // Act & Assert
      assertThatThrownBy(
              () ->
                  permissionService.assignRole(
                      TestConstants.TEST_USER_ID, "USER", TestConstants.TEST_ADMIN_ID))
          .isInstanceOf(PermissionDeniedException.class)
          .hasMessageContaining("user-roles:assign-basic");
    }

    @Test
    @DisplayName("should succeed when granter has elevated permission for elevated role")
    void assignRoleShouldSucceedWithElevatedPermissionForElevatedRole() {
      // Arrange
      when(userRoleRepository.findActivePermissionIdsByUserId(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(Set.of(TestConstants.PERM_ASSIGN_ELEVATED));
      when(resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_ADMIN_ID))
          .thenReturn(List.of());
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(List.of());

      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      when(userRepository.findByIdAndDeletedFalse(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));

      var role = new Role();
      role.setId("MANAGER");
      when(roleRepository.findByIdAndDeletedFalse("MANAGER")).thenReturn(Optional.of(role));

      when(userRoleRepository.findByUserIdAndRoleIdAndRevokedAtIsNull(
              TestConstants.TEST_USER_ID, "MANAGER"))
          .thenReturn(Optional.empty());

      // Act
      permissionService.assignRole(
          TestConstants.TEST_USER_ID, "MANAGER", TestConstants.TEST_ADMIN_ID);

      // Assert
      var captor = ArgumentCaptor.forClass(UserRole.class);
      verify(userRoleRepository).save(captor.capture());
      assertThat(captor.getValue().getUserId()).isEqualTo(TestConstants.TEST_USER_ID);
      assertThat(captor.getValue().getRoleId()).isEqualTo("MANAGER");
      assertThat(captor.getValue().getGrantedBy()).isEqualTo(TestConstants.TEST_ADMIN_ID);

      verify(auditService).logPermissionChange(any(PermissionChangeEvent.class));
      verify(permissionCacheService).invalidateCache(TestConstants.TEST_USER_ID);
    }

    @Test
    @DisplayName("should succeed when granter has basic permission for basic role")
    void assignRoleShouldSucceedWithBasicPermissionForBasicRole() {
      // Arrange
      when(userRoleRepository.findActivePermissionIdsByUserId(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(Set.of(TestConstants.PERM_ASSIGN_BASIC));
      when(resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_ADMIN_ID))
          .thenReturn(List.of());
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(List.of());

      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      when(userRepository.findByIdAndDeletedFalse(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));

      var role = new Role();
      role.setId("ACCOUNTANT");
      when(roleRepository.findByIdAndDeletedFalse("ACCOUNTANT")).thenReturn(Optional.of(role));

      when(userRoleRepository.findByUserIdAndRoleIdAndRevokedAtIsNull(
              TestConstants.TEST_USER_ID, "ACCOUNTANT"))
          .thenReturn(Optional.empty());

      // Act
      permissionService.assignRole(
          TestConstants.TEST_USER_ID, "ACCOUNTANT", TestConstants.TEST_ADMIN_ID);

      // Assert
      verify(userRoleRepository).save(any(UserRole.class));
      verify(auditService).logPermissionChange(any(PermissionChangeEvent.class));
      verify(permissionCacheService).invalidateCache(TestConstants.TEST_USER_ID);
    }

    @Test
    @DisplayName("should throw DuplicateRoleAssignmentException when role already assigned")
    void assignRoleShouldThrowWhenRoleAlreadyAssigned() {
      // Arrange
      when(userRoleRepository.findActivePermissionIdsByUserId(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(Set.of(TestConstants.PERM_ASSIGN_BASIC));
      when(resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_ADMIN_ID))
          .thenReturn(List.of());
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(List.of());

      var user = new User();
      user.setId(TestConstants.TEST_USER_ID);
      when(userRepository.findByIdAndDeletedFalse(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.of(user));

      var role = new Role();
      role.setId("USER");
      when(roleRepository.findByIdAndDeletedFalse("USER")).thenReturn(Optional.of(role));

      // Existing assignment
      var existingUserRole = new UserRole();
      when(userRoleRepository.findByUserIdAndRoleIdAndRevokedAtIsNull(
              TestConstants.TEST_USER_ID, "USER"))
          .thenReturn(Optional.of(existingUserRole));

      // Act & Assert
      assertThatThrownBy(
              () ->
                  permissionService.assignRole(
                      TestConstants.TEST_USER_ID, "USER", TestConstants.TEST_ADMIN_ID))
          .isInstanceOf(DuplicateRoleAssignmentException.class);
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when user not found")
    void assignRoleShouldThrowWhenUserNotFound() {
      // Arrange
      when(userRoleRepository.findActivePermissionIdsByUserId(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(Set.of(TestConstants.PERM_ASSIGN_BASIC));
      when(resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_ADMIN_ID))
          .thenReturn(List.of());
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(List.of());

      when(userRepository.findByIdAndDeletedFalse(TestConstants.TEST_USER_ID))
          .thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(
              () ->
                  permissionService.assignRole(
                      TestConstants.TEST_USER_ID, "USER", TestConstants.TEST_ADMIN_ID))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("User not found");
    }
  }

  @Nested
  @DisplayName("revokeRole")
  class RevokeRoleTests {

    @Test
    @DisplayName("should throw ProtectedRoleException when revoking SYSTEM_ADMIN")
    void revokeRoleShouldThrowWhenAttemptingToRevokeSystemAdmin() {
      // Act & Assert
      assertThatThrownBy(
              () ->
                  permissionService.revokeRole(
                      TestConstants.TEST_USER_ID, "SYSTEM_ADMIN", TestConstants.TEST_ADMIN_ID))
          .isInstanceOf(ProtectedRoleException.class)
          .hasMessageContaining("SYSTEM_ADMIN role cannot be revoked via API");
    }

    @Test
    @DisplayName("should throw PermissionDeniedException when revoker lacks revoke permission")
    void revokeRoleShouldThrowWhenUserLacksRevokePermission() {
      // Arrange - revoker has no revoke permission
      when(userRoleRepository.findActivePermissionIdsByUserId(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(Set.of(TestConstants.PERM_ASSIGN_BASIC));
      when(resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_ADMIN_ID))
          .thenReturn(List.of());
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(List.of());

      // Act & Assert
      assertThatThrownBy(
              () ->
                  permissionService.revokeRole(
                      TestConstants.TEST_USER_ID, "USER", TestConstants.TEST_ADMIN_ID))
          .isInstanceOf(PermissionDeniedException.class)
          .hasMessageContaining("user-roles:revoke");
    }

    @Test
    @DisplayName("should succeed when revoker has revoke permission")
    void revokeRoleShouldSucceedWithRevokePermission() {
      // Arrange
      when(userRoleRepository.findActivePermissionIdsByUserId(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(Set.of(TestConstants.PERM_REVOKE));
      when(resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_ADMIN_ID))
          .thenReturn(List.of());
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(List.of());

      var userRole = new UserRole();
      userRole.setUserId(TestConstants.TEST_USER_ID);
      userRole.setRoleId("ACCOUNTANT");
      when(userRoleRepository.findByUserIdAndRoleIdAndRevokedAtIsNull(
              TestConstants.TEST_USER_ID, "ACCOUNTANT"))
          .thenReturn(Optional.of(userRole));

      // Act
      permissionService.revokeRole(
          TestConstants.TEST_USER_ID, "ACCOUNTANT", TestConstants.TEST_ADMIN_ID);

      // Assert
      var captor = ArgumentCaptor.forClass(UserRole.class);
      verify(userRoleRepository).save(captor.capture());
      assertThat(captor.getValue().getRevokedAt()).isNotNull();
      assertThat(captor.getValue().getRevokedBy()).isEqualTo(TestConstants.TEST_ADMIN_ID);

      verify(auditService).logPermissionChange(any(PermissionChangeEvent.class));
      verify(permissionCacheService).invalidateCache(TestConstants.TEST_USER_ID);
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when role assignment not found")
    void revokeRoleShouldThrowWhenAssignmentNotFound() {
      // Arrange
      when(userRoleRepository.findActivePermissionIdsByUserId(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(Set.of(TestConstants.PERM_REVOKE));
      when(resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_ADMIN_ID))
          .thenReturn(List.of());
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_ADMIN_ID), any()))
          .thenReturn(List.of());

      when(userRoleRepository.findByUserIdAndRoleIdAndRevokedAtIsNull(
              TestConstants.TEST_USER_ID, "USER"))
          .thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(
              () ->
                  permissionService.revokeRole(
                      TestConstants.TEST_USER_ID, "USER", TestConstants.TEST_ADMIN_ID))
          .isInstanceOf(ResourceNotFoundException.class);
    }
  }

  @Nested
  @DisplayName("getUserRoles")
  class GetUserRolesTests {

    @Test
    @DisplayName("should return all active roles for user")
    void shouldReturnActiveRolesForUser() {
      // Arrange
      var userRole1 = new UserRole();
      userRole1.setRoleId("USER");
      var userRole2 = new UserRole();
      userRole2.setRoleId("ACCOUNTANT");

      when(userRoleRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_USER_ID))
          .thenReturn(List.of(userRole1, userRole2));

      var role1 = new Role();
      role1.setId("USER");
      role1.setName("User");
      var role2 = new Role();
      role2.setId("ACCOUNTANT");
      role2.setName("Accountant");

      when(roleRepository.findByIdAndDeletedFalse("USER")).thenReturn(Optional.of(role1));
      when(roleRepository.findByIdAndDeletedFalse("ACCOUNTANT")).thenReturn(Optional.of(role2));

      // Act
      var result = permissionService.getUserRoles(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result).hasSize(2);
      assertThat(result).extracting(Role::getId).containsExactlyInAnyOrder("USER", "ACCOUNTANT");
    }

    @Test
    @DisplayName("should return empty list when user has no roles")
    void shouldReturnEmptyWhenNoRoles() {
      // Arrange
      when(userRoleRepository.findByUserIdAndRevokedAtIsNull(TestConstants.TEST_USER_ID))
          .thenReturn(List.of());

      // Act
      var result = permissionService.getUserRoles(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("getPermissionsAtPointInTime")
  class GetPermissionsAtPointInTimeTests {

    @Test
    @DisplayName("should return permissions active at specified time")
    void shouldReturnPermissionsAtPointInTime() {
      // Arrange
      var pointInTime = Instant.now().minusSeconds(3600); // 1 hour ago

      var userRole = new UserRole();
      userRole.setRoleId("USER");
      when(userRoleRepository.findRolesAtPointInTime(TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of(userRole));

      when(resourcePermissionRepository.findPermissionsAtPointInTime(
              TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of());

      when(rolePermissionRepository.findByRoleIdAndRevokedAtIsNull("USER")).thenReturn(List.of());

      // Act
      var result =
          permissionService.getPermissionsAtPointInTime(TestConstants.TEST_USER_ID, pointInTime);

      // Assert
      assertThat(result).isNotNull();
    }
  }
}
