package org.budgetanalyzer.permission.service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.domain.Role;
import org.budgetanalyzer.permission.domain.UserRole;
import org.budgetanalyzer.permission.event.PermissionChangeEvent;
import org.budgetanalyzer.permission.repository.DelegationRepository;
import org.budgetanalyzer.permission.repository.ResourcePermissionRepository;
import org.budgetanalyzer.permission.repository.RolePermissionRepository;
import org.budgetanalyzer.permission.repository.RoleRepository;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;
import org.budgetanalyzer.permission.service.dto.EffectivePermissions;
import org.budgetanalyzer.permission.service.exception.DuplicateRoleAssignmentException;
import org.budgetanalyzer.permission.service.exception.PermissionDeniedException;
import org.budgetanalyzer.permission.service.exception.ProtectedRoleException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

/**
 * Core service for permission operations.
 *
 * <p>Handles role assignments, permission queries, and point-in-time permission lookups.
 */
@Service
@Transactional(readOnly = true)
public class PermissionService {

  // Role classification for assignment governance
  private static final Set<String> BASIC_ROLES = Set.of("USER", "ACCOUNTANT", "AUDITOR");
  private static final Set<String> ELEVATED_ROLES = Set.of("MANAGER", "ORG_ADMIN");
  private static final String PROTECTED_ROLE = "SYSTEM_ADMIN";

  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;
  private final RoleRepository roleRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final ResourcePermissionRepository resourcePermissionRepository;
  private final DelegationRepository delegationRepository;
  private final AuditService auditService;
  private final PermissionCacheService permissionCacheService;

  /**
   * Constructs a new PermissionService.
   *
   * @param userRepository the user repository
   * @param userRoleRepository the user role repository
   * @param roleRepository the role repository
   * @param rolePermissionRepository the role permission repository
   * @param resourcePermissionRepository the resource permission repository
   * @param delegationRepository the delegation repository
   * @param auditService the audit service
   * @param permissionCacheService the permission cache service
   */
  public PermissionService(
      UserRepository userRepository,
      UserRoleRepository userRoleRepository,
      RoleRepository roleRepository,
      RolePermissionRepository rolePermissionRepository,
      ResourcePermissionRepository resourcePermissionRepository,
      DelegationRepository delegationRepository,
      AuditService auditService,
      PermissionCacheService permissionCacheService) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
    this.roleRepository = roleRepository;
    this.rolePermissionRepository = rolePermissionRepository;
    this.resourcePermissionRepository = resourcePermissionRepository;
    this.delegationRepository = delegationRepository;
    this.auditService = auditService;
    this.permissionCacheService = permissionCacheService;
  }

  /**
   * Gets the effective permissions for a user from all sources.
   *
   * @param userId the user ID
   * @return the effective permissions
   */
  public EffectivePermissions getEffectivePermissions(String userId) {
    // 1. Get role-based permissions
    var rolePermissions = userRoleRepository.findActivePermissionIdsByUserId(userId, Instant.now());

    // 2. Get resource-specific permissions
    var resourcePermissions = resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(userId);

    // 3. Get delegated permissions
    var delegations = delegationRepository.findActiveDelegationsForUser(userId, Instant.now());

    // 4. Build and return service-layer DTO
    return new EffectivePermissions(rolePermissions, resourcePermissions, delegations);
  }

  /**
   * Gets all active roles for a user.
   *
   * @param userId the user ID
   * @return list of active roles
   */
  public List<Role> getUserRoles(String userId) {
    var userRoles = userRoleRepository.findByUserIdAndRevokedAtIsNull(userId);

    return userRoles.stream()
        .map(ur -> roleRepository.findByIdAndDeletedFalse(ur.getRoleId()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  /**
   * Assigns a role to a user.
   *
   * @param userId the user ID to assign the role to
   * @param roleId the role ID to assign
   * @param grantedBy the user ID performing the assignment
   * @throws ProtectedRoleException if trying to assign SYSTEM_ADMIN
   * @throws PermissionDeniedException if granter lacks permission
   * @throws DuplicateRoleAssignmentException if role already assigned
   */
  @Transactional
  public void assignRole(String userId, String roleId, String grantedBy) {
    // 1. SYSTEM_ADMIN cannot be assigned via API - database only
    if (PROTECTED_ROLE.equals(roleId)) {
      throw new ProtectedRoleException(
          "SYSTEM_ADMIN role cannot be assigned via API. Use database directly.");
    }

    // 2. Check granter has permission to assign this role level
    var granterPermissions = getEffectivePermissions(grantedBy).getAllPermissionIds();

    if (ELEVATED_ROLES.contains(roleId)) {
      if (!granterPermissions.contains("user-roles:assign-elevated")) {
        throw new PermissionDeniedException(
            "Cannot assign elevated role: "
                + roleId
                + ". Requires 'user-roles:assign-elevated' permission.",
            "INSUFFICIENT_PERMISSION_FOR_ELEVATED_ROLE");
      }
    } else if (BASIC_ROLES.contains(roleId)) {
      if (!granterPermissions.contains("user-roles:assign-basic")
          && !granterPermissions.contains("user-roles:assign-elevated")) {
        throw new PermissionDeniedException(
            "Cannot assign role: " + roleId + ". Requires 'user-roles:assign-basic' permission.",
            "INSUFFICIENT_PERMISSION_FOR_BASIC_ROLE");
      }
    } else {
      // Custom role - require elevated permission
      if (!granterPermissions.contains("user-roles:assign-elevated")) {
        throw new PermissionDeniedException(
            "Cannot assign custom role: " + roleId, "INSUFFICIENT_PERMISSION_FOR_CUSTOM_ROLE");
      }
    }

    // 3. Validate user exists and is not soft-deleted
    userRepository
        .findByIdAndDeletedFalse(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

    // 4. Validate role exists and is not soft-deleted
    roleRepository
        .findByIdAndDeletedFalse(roleId)
        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

    // 5. Check if active assignment already exists
    if (userRoleRepository.findByUserIdAndRoleIdAndRevokedAtIsNull(userId, roleId).isPresent()) {
      throw new DuplicateRoleAssignmentException(userId, roleId);
    }

    // 6. Create new UserRole entry (re-granting creates new row)
    var userRole = new UserRole();
    userRole.setUserId(userId);
    userRole.setRoleId(roleId);
    userRole.setGrantedAt(Instant.now());
    userRole.setGrantedBy(grantedBy);
    userRoleRepository.save(userRole);

    // 7. Log to audit
    auditService.logPermissionChange(PermissionChangeEvent.roleAssigned(userId, roleId, grantedBy));

    // 8. Invalidate cache
    permissionCacheService.invalidateCache(userId);
  }

  /**
   * Revokes a role from a user.
   *
   * @param userId the user ID to revoke the role from
   * @param roleId the role ID to revoke
   * @param revokedBy the user ID performing the revocation
   * @throws ProtectedRoleException if trying to revoke SYSTEM_ADMIN
   * @throws PermissionDeniedException if revoker lacks permission
   */
  @Transactional
  public void revokeRole(String userId, String roleId, String revokedBy) {
    // 1. SYSTEM_ADMIN role cannot be revoked via API
    if (PROTECTED_ROLE.equals(roleId)) {
      throw new ProtectedRoleException(
          "SYSTEM_ADMIN role cannot be revoked via API. Use database directly.");
    }

    // 2. Check revoker has permission
    var revokerPermissions = getEffectivePermissions(revokedBy).getAllPermissionIds();
    if (!revokerPermissions.contains("user-roles:revoke")) {
      throw new PermissionDeniedException(
          "Cannot revoke roles. Requires 'user-roles:revoke' permission.",
          "INSUFFICIENT_PERMISSION_FOR_REVOKE");
    }

    // 3. Find active UserRole entry
    var userRole =
        userRoleRepository
            .findByUserIdAndRoleIdAndRevokedAtIsNull(userId, roleId)
            .orElseThrow(() -> new ResourceNotFoundException("Active role assignment not found"));

    // 4. Set revokedAt = now, revokedBy = revokedBy (temporal revocation, not delete)
    userRole.setRevokedAt(Instant.now());
    userRole.setRevokedBy(revokedBy);
    userRoleRepository.save(userRole);

    // 5. Log to audit
    auditService.logPermissionChange(PermissionChangeEvent.roleRevoked(userId, roleId, revokedBy));

    // 6. Invalidate cache
    permissionCacheService.invalidateCache(userId);
  }

  /**
   * Gets permissions at a specific point in time for compliance/audit.
   *
   * @param userId the user ID
   * @param pointInTime the point in time to query
   * @return the effective permissions at that time
   */
  public EffectivePermissions getPermissionsAtPointInTime(String userId, Instant pointInTime) {
    // Query all temporal tables with point-in-time filters
    var rolesAtTime = userRoleRepository.findRolesAtPointInTime(userId, pointInTime);
    var resourcePermsAtTime =
        resourcePermissionRepository.findPermissionsAtPointInTime(userId, pointInTime);

    // Build permissions from roles at that time
    var rolePermissions =
        rolesAtTime.stream()
            .flatMap(ur -> getRolePermissionsAtPointInTime(ur.getRoleId(), pointInTime).stream())
            .collect(Collectors.toSet());

    return new EffectivePermissions(rolePermissions, resourcePermsAtTime, List.of());
  }

  /**
   * Gets permission IDs for a role at a specific point in time.
   *
   * @param roleId the role ID
   * @param pointInTime the point in time to query
   * @return set of permission IDs
   */
  private Set<String> getRolePermissionsAtPointInTime(String roleId, Instant pointInTime) {
    // Get active role-permissions at that point in time
    return rolePermissionRepository.findByRoleIdAndRevokedAtIsNull(roleId).stream()
        .filter(
            rp -> rp.getGrantedAt().isBefore(pointInTime) || rp.getGrantedAt().equals(pointInTime))
        .filter(rp -> rp.getRevokedAt() == null || rp.getRevokedAt().isAfter(pointInTime))
        .map(rp -> rp.getPermissionId())
        .collect(Collectors.toSet());
  }
}
