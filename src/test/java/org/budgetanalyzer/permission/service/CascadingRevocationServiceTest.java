package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.domain.Delegation;
import org.budgetanalyzer.permission.domain.ResourcePermission;
import org.budgetanalyzer.permission.domain.RolePermission;
import org.budgetanalyzer.permission.domain.UserRole;
import org.budgetanalyzer.permission.event.PermissionChangeEvent;
import org.budgetanalyzer.permission.repository.DelegationRepository;
import org.budgetanalyzer.permission.repository.ResourcePermissionRepository;
import org.budgetanalyzer.permission.repository.RolePermissionRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CascadingRevocationService")
class CascadingRevocationServiceTest {

  @Mock private UserRoleRepository userRoleRepository;
  @Mock private RolePermissionRepository rolePermissionRepository;
  @Mock private ResourcePermissionRepository resourcePermissionRepository;
  @Mock private DelegationRepository delegationRepository;
  @Mock private AuditService auditService;
  @Mock private PermissionCacheService permissionCacheService;

  private CascadingRevocationService cascadingRevocationService;

  @BeforeEach
  void setUp() {
    cascadingRevocationService =
        new CascadingRevocationService(
            userRoleRepository,
            rolePermissionRepository,
            resourcePermissionRepository,
            delegationRepository,
            auditService,
            permissionCacheService);
  }

  @Nested
  @DisplayName("revokeAllForUser")
  class RevokeAllForUserTests {

    @Test
    @DisplayName("should revoke all user roles on user delete")
    void shouldRevokeAllUserRolesOnUserDelete() {
      // Arrange
      var userRole1 = new UserRole();
      userRole1.setUserId(TestConstants.TEST_USER_ID);
      userRole1.setRoleId("USER");
      var userRole2 = new UserRole();
      userRole2.setUserId(TestConstants.TEST_USER_ID);
      userRole2.setRoleId("ACCOUNTANT");

      when(userRoleRepository.findActiveByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(List.of(userRole1, userRole2));
      when(resourcePermissionRepository.findActiveByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(List.of());
      when(delegationRepository.findActiveByUserId(eq(TestConstants.TEST_USER_ID), any()))
          .thenReturn(List.of());

      // Act
      cascadingRevocationService.revokeAllForUser(
          TestConstants.TEST_USER_ID, TestConstants.TEST_ADMIN_ID);

      // Assert
      var captor = ArgumentCaptor.forClass(UserRole.class);
      verify(userRoleRepository, times(2)).save(captor.capture());

      var savedRoles = captor.getAllValues();
      assertThat(savedRoles).allMatch(ur -> ur.getRevokedAt() != null);
      assertThat(savedRoles).allMatch(ur -> ur.getRevokedBy().equals(TestConstants.TEST_ADMIN_ID));

      verify(auditService).logPermissionChange(any(PermissionChangeEvent.class));
      verify(permissionCacheService).invalidateCache(TestConstants.TEST_USER_ID);
    }

    @Test
    @DisplayName("should revoke all resource permissions on user delete")
    void shouldRevokeAllResourcePermissionsOnUserDelete() {
      // Arrange
      var resourcePerm = new ResourcePermission();
      resourcePerm.setUserId(TestConstants.TEST_USER_ID);

      when(userRoleRepository.findActiveByUserId(TestConstants.TEST_USER_ID)).thenReturn(List.of());
      when(resourcePermissionRepository.findActiveByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(List.of(resourcePerm));
      when(delegationRepository.findActiveByUserId(eq(TestConstants.TEST_USER_ID), any()))
          .thenReturn(List.of());

      // Act
      cascadingRevocationService.revokeAllForUser(
          TestConstants.TEST_USER_ID, TestConstants.TEST_ADMIN_ID);

      // Assert
      var captor = ArgumentCaptor.forClass(ResourcePermission.class);
      verify(resourcePermissionRepository).save(captor.capture());
      assertThat(captor.getValue().getRevokedAt()).isNotNull();
      assertThat(captor.getValue().getRevokedBy()).isEqualTo(TestConstants.TEST_ADMIN_ID);
    }

    @Test
    @DisplayName("should revoke all delegations on user delete")
    void shouldRevokeAllDelegationsOnUserDelete() {
      // Arrange
      var delegation = new Delegation();
      delegation.setDelegatorId(TestConstants.TEST_USER_ID);

      when(userRoleRepository.findActiveByUserId(TestConstants.TEST_USER_ID)).thenReturn(List.of());
      when(resourcePermissionRepository.findActiveByUserId(TestConstants.TEST_USER_ID))
          .thenReturn(List.of());
      when(delegationRepository.findActiveByUserId(eq(TestConstants.TEST_USER_ID), any()))
          .thenReturn(List.of(delegation));

      // Act
      cascadingRevocationService.revokeAllForUser(
          TestConstants.TEST_USER_ID, TestConstants.TEST_ADMIN_ID);

      // Assert
      var captor = ArgumentCaptor.forClass(Delegation.class);
      verify(delegationRepository).save(captor.capture());
      assertThat(captor.getValue().getRevokedAt()).isNotNull();
      assertThat(captor.getValue().getRevokedBy()).isEqualTo(TestConstants.TEST_ADMIN_ID);
    }
  }

  @Nested
  @DisplayName("revokeAllForRole")
  class RevokeAllForRoleTests {

    @Test
    @DisplayName("should revoke all UserRole entries for deleted role")
    void shouldRevokeAllUserRoleEntriesForRole() {
      // Arrange
      var userRole = new UserRole();
      userRole.setUserId(TestConstants.TEST_USER_ID);
      userRole.setRoleId("MANAGER");

      when(userRoleRepository.findActiveByRoleId("MANAGER")).thenReturn(List.of(userRole));
      when(rolePermissionRepository.findActiveByRoleId("MANAGER")).thenReturn(List.of());

      // Act
      cascadingRevocationService.revokeAllForRole("MANAGER", TestConstants.TEST_ADMIN_ID);

      // Assert
      var captor = ArgumentCaptor.forClass(UserRole.class);
      verify(userRoleRepository).save(captor.capture());
      assertThat(captor.getValue().getRevokedAt()).isNotNull();
      assertThat(captor.getValue().getRevokedBy()).isEqualTo(TestConstants.TEST_ADMIN_ID);

      verify(permissionCacheService).invalidateCache(TestConstants.TEST_USER_ID);
    }

    @Test
    @DisplayName("should revoke all RolePermission entries for deleted role")
    void shouldRevokeAllRolePermissionEntriesForRole() {
      // Arrange
      var rolePermission = new RolePermission();
      rolePermission.setRoleId("MANAGER");
      rolePermission.setPermissionId("transactions:write");

      when(userRoleRepository.findActiveByRoleId("MANAGER")).thenReturn(List.of());
      when(rolePermissionRepository.findActiveByRoleId("MANAGER"))
          .thenReturn(List.of(rolePermission));

      // Act
      cascadingRevocationService.revokeAllForRole("MANAGER", TestConstants.TEST_ADMIN_ID);

      // Assert
      var captor = ArgumentCaptor.forClass(RolePermission.class);
      verify(rolePermissionRepository).save(captor.capture());
      assertThat(captor.getValue().getRevokedAt()).isNotNull();
      assertThat(captor.getValue().getRevokedBy()).isEqualTo(TestConstants.TEST_ADMIN_ID);
    }

    @Test
    @DisplayName("should invalidate cache for all affected users")
    void shouldInvalidateCacheForAllAffectedUsers() {
      // Arrange
      var userRole1 = new UserRole();
      userRole1.setUserId(TestConstants.TEST_USER_ID);
      userRole1.setRoleId("MANAGER");
      var userRole2 = new UserRole();
      userRole2.setUserId(TestConstants.TEST_DELEGATEE_ID);
      userRole2.setRoleId("MANAGER");

      when(userRoleRepository.findActiveByRoleId("MANAGER"))
          .thenReturn(List.of(userRole1, userRole2));
      when(rolePermissionRepository.findActiveByRoleId("MANAGER")).thenReturn(List.of());

      // Act
      cascadingRevocationService.revokeAllForRole("MANAGER", TestConstants.TEST_ADMIN_ID);

      // Assert
      verify(permissionCacheService).invalidateCache(TestConstants.TEST_USER_ID);
      verify(permissionCacheService).invalidateCache(TestConstants.TEST_DELEGATEE_ID);
    }
  }

  @Nested
  @DisplayName("revokeAllForPermission")
  class RevokeAllForPermissionTests {

    @Test
    @DisplayName("should revoke all RolePermission entries for deleted permission")
    void shouldRevokeAllRolePermissionEntriesForPermission() {
      // Arrange
      var rolePermission = new RolePermission();
      rolePermission.setRoleId("USER");
      rolePermission.setPermissionId("transactions:read");

      when(rolePermissionRepository.findActiveByPermissionId("transactions:read"))
          .thenReturn(List.of(rolePermission));
      when(userRoleRepository.findActiveByRoleId("USER")).thenReturn(List.of());

      // Act
      cascadingRevocationService.revokeAllForPermission(
          "transactions:read", TestConstants.TEST_ADMIN_ID);

      // Assert
      var captor = ArgumentCaptor.forClass(RolePermission.class);
      verify(rolePermissionRepository).save(captor.capture());
      assertThat(captor.getValue().getRevokedAt()).isNotNull();
      assertThat(captor.getValue().getRevokedBy()).isEqualTo(TestConstants.TEST_ADMIN_ID);

      verify(auditService).logPermissionChange(any(PermissionChangeEvent.class));
    }
  }
}
