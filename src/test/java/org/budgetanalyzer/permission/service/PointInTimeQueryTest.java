package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.domain.RolePermission;
import org.budgetanalyzer.permission.domain.UserRole;
import org.budgetanalyzer.permission.repository.DelegationRepository;
import org.budgetanalyzer.permission.repository.ResourcePermissionRepository;
import org.budgetanalyzer.permission.repository.RolePermissionRepository;
import org.budgetanalyzer.permission.repository.RoleRepository;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;

/**
 * Tests for point-in-time permission queries.
 *
 * <p>Validates that historical permission states can be accurately queried for compliance and audit
 * purposes.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Point-in-time Query Tests")
class PointInTimeQueryTest {

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
  @DisplayName("getPermissionsAtPointInTime")
  class GetPermissionsAtPointInTimeTests {

    @Test
    @DisplayName("should return permissions that were active at specified time")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    void shouldReturnPermissionsActiveAtSpecifiedTime() {
      // Arrange
      var pointInTime = Instant.parse("2024-06-01T12:00:00Z");

      // User had USER role at that time
      var userRole = new UserRole();
      userRole.setUserId(TestConstants.TEST_USER_ID);
      userRole.setRoleId("USER");
      userRole.setGrantedAt(Instant.parse("2024-01-01T00:00:00Z"));
      userRole.setRevokedAt(null);

      when(userRoleRepository.findRolesAtPointInTime(TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of(userRole));

      when(resourcePermissionRepository.findPermissionsAtPointInTime(
              TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of());

      // Role had these permissions at that time
      var rolePermission = new RolePermission();
      rolePermission.setRoleId("USER");
      rolePermission.setPermissionId("transactions:read");
      rolePermission.setGrantedAt(Instant.parse("2024-01-01T00:00:00Z"));

      when(rolePermissionRepository.findByRoleIdAndRevokedAtIsNull("USER"))
          .thenReturn(List.of(rolePermission));

      // Act
      var result =
          permissionService.getPermissionsAtPointInTime(TestConstants.TEST_USER_ID, pointInTime);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getAllPermissionIds()).contains("transactions:read");
    }

    @Test
    @DisplayName("should not include permissions granted after query time")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    void shouldNotIncludePermissionsGrantedAfterQueryTime() {
      // Arrange
      var pointInTime = Instant.parse("2024-06-01T12:00:00Z");

      // User role granted after query time
      var userRole = new UserRole();
      userRole.setUserId(TestConstants.TEST_USER_ID);
      userRole.setRoleId("MANAGER");
      userRole.setGrantedAt(Instant.parse("2024-07-01T00:00:00Z")); // After query time

      when(userRoleRepository.findRolesAtPointInTime(TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of()); // Repository should filter this out

      when(resourcePermissionRepository.findPermissionsAtPointInTime(
              TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of());

      // Act
      var result =
          permissionService.getPermissionsAtPointInTime(TestConstants.TEST_USER_ID, pointInTime);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getAllPermissionIds()).isEmpty();
    }

    @Test
    @DisplayName("should not include permissions revoked before query time")
    void shouldNotIncludePermissionsRevokedBeforeQueryTime() {
      // Arrange
      var pointInTime = Instant.parse("2024-06-01T12:00:00Z");

      // Role was revoked before query time
      when(userRoleRepository.findRolesAtPointInTime(TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of()); // Repository filters out revoked roles

      when(resourcePermissionRepository.findPermissionsAtPointInTime(
              TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of());

      // Act
      var result =
          permissionService.getPermissionsAtPointInTime(TestConstants.TEST_USER_ID, pointInTime);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getAllPermissionIds()).isEmpty();
    }

    @Test
    @DisplayName("should include permissions from multiple roles at query time")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    void shouldIncludePermissionsFromMultipleRoles() {
      // Arrange
      var pointInTime = Instant.parse("2024-06-01T12:00:00Z");

      var userRole = new UserRole();
      userRole.setRoleId("USER");
      userRole.setGrantedAt(Instant.parse("2024-01-01T00:00:00Z"));

      var accountantRole = new UserRole();
      accountantRole.setRoleId("ACCOUNTANT");
      accountantRole.setGrantedAt(Instant.parse("2024-02-01T00:00:00Z"));

      when(userRoleRepository.findRolesAtPointInTime(TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of(userRole, accountantRole));

      when(resourcePermissionRepository.findPermissionsAtPointInTime(
              TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of());

      var userRolePermission = new RolePermission();
      userRolePermission.setRoleId("USER");
      userRolePermission.setPermissionId("transactions:read");
      userRolePermission.setGrantedAt(Instant.parse("2024-01-01T00:00:00Z"));

      var accountantRolePermission = new RolePermission();
      accountantRolePermission.setRoleId("ACCOUNTANT");
      accountantRolePermission.setPermissionId("budgets:write");
      accountantRolePermission.setGrantedAt(Instant.parse("2024-01-01T00:00:00Z"));

      when(rolePermissionRepository.findByRoleIdAndRevokedAtIsNull("USER"))
          .thenReturn(List.of(userRolePermission));
      when(rolePermissionRepository.findByRoleIdAndRevokedAtIsNull("ACCOUNTANT"))
          .thenReturn(List.of(accountantRolePermission));

      // Act
      var result =
          permissionService.getPermissionsAtPointInTime(TestConstants.TEST_USER_ID, pointInTime);

      // Assert
      assertThat(result.getAllPermissionIds())
          .containsExactlyInAnyOrder("transactions:read", "budgets:write");
    }

    @Test
    @DisplayName("should handle user with no roles at query time")
    void shouldHandleUserWithNoRolesAtQueryTime() {
      // Arrange
      var pointInTime = Instant.parse("2024-01-01T00:00:00Z"); // Before user was created

      when(userRoleRepository.findRolesAtPointInTime(TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of());

      when(resourcePermissionRepository.findPermissionsAtPointInTime(
              TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of());

      // Act
      var result =
          permissionService.getPermissionsAtPointInTime(TestConstants.TEST_USER_ID, pointInTime);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getAllPermissionIds()).isEmpty();
    }

    @Test
    @DisplayName("should filter role permissions by grant time")
    void shouldFilterRolePermissionsByGrantTime() {
      // Arrange
      var pointInTime = Instant.parse("2024-06-01T12:00:00Z");

      var userRole = new UserRole();
      userRole.setRoleId("USER");
      userRole.setGrantedAt(Instant.parse("2024-01-01T00:00:00Z"));

      when(userRoleRepository.findRolesAtPointInTime(TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of(userRole));

      when(resourcePermissionRepository.findPermissionsAtPointInTime(
              TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of());

      // Permission granted before query time (should be included)
      var oldPermission = new RolePermission();
      oldPermission.setRoleId("USER");
      oldPermission.setPermissionId("transactions:read");
      oldPermission.setGrantedAt(Instant.parse("2024-01-01T00:00:00Z"));

      // Permission granted after query time (should be excluded)
      var newPermission = new RolePermission();
      newPermission.setRoleId("USER");
      newPermission.setPermissionId("transactions:delete");
      newPermission.setGrantedAt(Instant.parse("2024-07-01T00:00:00Z"));

      when(rolePermissionRepository.findByRoleIdAndRevokedAtIsNull("USER"))
          .thenReturn(List.of(oldPermission, newPermission));

      // Act
      var result =
          permissionService.getPermissionsAtPointInTime(TestConstants.TEST_USER_ID, pointInTime);

      // Assert
      assertThat(result.getAllPermissionIds()).contains("transactions:read");
      assertThat(result.getAllPermissionIds()).doesNotContain("transactions:delete");
    }

    @Test
    @DisplayName("should exclude role permissions revoked before query time")
    void shouldExcludeRolePermissionsRevokedBeforeQueryTime() {
      // Arrange
      var pointInTime = Instant.parse("2024-06-01T12:00:00Z");

      var userRole = new UserRole();
      userRole.setRoleId("USER");
      userRole.setGrantedAt(Instant.parse("2024-01-01T00:00:00Z"));

      when(userRoleRepository.findRolesAtPointInTime(TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of(userRole));

      when(resourcePermissionRepository.findPermissionsAtPointInTime(
              TestConstants.TEST_USER_ID, pointInTime))
          .thenReturn(List.of());

      // Permission was revoked before query time
      var revokedPermission = new RolePermission();
      revokedPermission.setRoleId("USER");
      revokedPermission.setPermissionId("transactions:delete");
      revokedPermission.setGrantedAt(Instant.parse("2024-01-01T00:00:00Z"));
      revokedPermission.setRevokedAt(Instant.parse("2024-03-01T00:00:00Z")); // Revoked before query

      when(rolePermissionRepository.findByRoleIdAndRevokedAtIsNull("USER"))
          .thenReturn(List.of()); // Repository returns only non-revoked

      // Act
      var result =
          permissionService.getPermissionsAtPointInTime(TestConstants.TEST_USER_ID, pointInTime);

      // Assert
      assertThat(result.getAllPermissionIds()).doesNotContain("transactions:delete");
    }
  }

  @Nested
  @DisplayName("Re-granting revoked roles")
  class ReGrantingRevokedRolesTests {

    @Test
    @DisplayName("should preserve history when role is re-granted")
    @SuppressWarnings("checkstyle:VariableDeclarationUsageDistance")
    void shouldPreserveHistoryWhenRoleIsReGranted() {
      // Scenario: User had MANAGER role, it was revoked, then re-granted
      // Point-in-time query should correctly identify which grant was active

      var firstGrantTime = Instant.parse("2024-01-01T00:00:00Z");
      var revokeTime = Instant.parse("2024-03-01T00:00:00Z");
      var secondGrantTime = Instant.parse("2024-05-01T00:00:00Z");

      // Query during first grant period
      var queryDuringFirstGrant = Instant.parse("2024-02-01T00:00:00Z");

      var firstGrant = new UserRole();
      firstGrant.setRoleId("MANAGER");
      firstGrant.setGrantedAt(firstGrantTime);
      firstGrant.setRevokedAt(revokeTime);

      when(userRoleRepository.findRolesAtPointInTime(
              TestConstants.TEST_USER_ID, queryDuringFirstGrant))
          .thenReturn(List.of(firstGrant));

      when(resourcePermissionRepository.findPermissionsAtPointInTime(
              TestConstants.TEST_USER_ID, queryDuringFirstGrant))
          .thenReturn(List.of());

      var managerPermission = new RolePermission();
      managerPermission.setRoleId("MANAGER");
      managerPermission.setPermissionId("team:manage");
      managerPermission.setGrantedAt(firstGrantTime);

      when(rolePermissionRepository.findByRoleIdAndRevokedAtIsNull("MANAGER"))
          .thenReturn(List.of(managerPermission));

      // Act
      var result =
          permissionService.getPermissionsAtPointInTime(
              TestConstants.TEST_USER_ID, queryDuringFirstGrant);

      // Assert
      assertThat(result.getAllPermissionIds()).contains("team:manage");
    }
  }
}
