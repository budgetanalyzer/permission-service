package org.budgetanalyzer.permission.service;

import java.time.Instant;
import java.util.HashSet;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.event.PermissionChangeEvent;
import org.budgetanalyzer.permission.repository.DelegationRepository;
import org.budgetanalyzer.permission.repository.ResourcePermissionRepository;
import org.budgetanalyzer.permission.repository.RolePermissionRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;

/**
 * Service for handling cascading revocations when parent entities are soft-deleted.
 *
 * <p>When a user, role, or permission is soft-deleted, this service revokes all related temporal
 * assignments to maintain data consistency.
 */
@Service
@Transactional
public class CascadingRevocationService {

  private final UserRoleRepository userRoleRepository;
  private final RolePermissionRepository rolePermissionRepository;
  private final ResourcePermissionRepository resourcePermissionRepository;
  private final DelegationRepository delegationRepository;
  private final AuditService auditService;
  private final PermissionCacheService permissionCacheService;

  /**
   * Constructs a new CascadingRevocationService.
   *
   * @param userRoleRepository the user role repository
   * @param rolePermissionRepository the role permission repository
   * @param resourcePermissionRepository the resource permission repository
   * @param delegationRepository the delegation repository
   * @param auditService the audit service
   * @param permissionCacheService the permission cache service
   */
  public CascadingRevocationService(
      UserRoleRepository userRoleRepository,
      RolePermissionRepository rolePermissionRepository,
      ResourcePermissionRepository resourcePermissionRepository,
      DelegationRepository delegationRepository,
      AuditService auditService,
      PermissionCacheService permissionCacheService) {
    this.userRoleRepository = userRoleRepository;
    this.rolePermissionRepository = rolePermissionRepository;
    this.resourcePermissionRepository = resourcePermissionRepository;
    this.delegationRepository = delegationRepository;
    this.auditService = auditService;
    this.permissionCacheService = permissionCacheService;
  }

  /**
   * Revokes all permissions for a user when the user is soft-deleted.
   *
   * @param userId the user ID
   * @param revokedBy the user performing the revocation
   */
  public void revokeAllForUser(String userId, String revokedBy) {
    var now = Instant.now();

    // 1. Revoke all UserRole entries for user
    userRoleRepository
        .findActiveByUserId(userId)
        .forEach(
            ur -> {
              ur.setRevokedAt(now);
              ur.setRevokedBy(revokedBy);
              userRoleRepository.save(ur);
            });

    // 2. Revoke all ResourcePermission entries for user
    resourcePermissionRepository
        .findActiveByUserId(userId)
        .forEach(
            rp -> {
              rp.setRevokedAt(now);
              rp.setRevokedBy(revokedBy);
              resourcePermissionRepository.save(rp);
            });

    // 3. Revoke all Delegation entries (as delegator or delegatee)
    delegationRepository
        .findActiveByUserId(userId, now)
        .forEach(
            d -> {
              d.setRevokedAt(now);
              d.setRevokedBy(revokedBy);
              delegationRepository.save(d);
            });

    // 4. Log to audit
    auditService.logPermissionChange(
        PermissionChangeEvent.cascadingRevocation("user", userId, revokedBy));

    // 5. Invalidate cache
    permissionCacheService.invalidateCache(userId);
  }

  /**
   * Revokes all permissions for a role when the role is soft-deleted.
   *
   * @param roleId the role ID
   * @param revokedBy the user performing the revocation
   */
  public void revokeAllForRole(String roleId, String revokedBy) {
    var now = Instant.now();

    // 1. Revoke all UserRole entries for role and collect affected users
    var affectedUserIds = new HashSet<String>();
    userRoleRepository
        .findActiveByRoleId(roleId)
        .forEach(
            ur -> {
              ur.setRevokedAt(now);
              ur.setRevokedBy(revokedBy);
              userRoleRepository.save(ur);
              affectedUserIds.add(ur.getUserId());
            });

    // 2. Revoke all RolePermission entries for role
    rolePermissionRepository
        .findActiveByRoleId(roleId)
        .forEach(
            rp -> {
              rp.setRevokedAt(now);
              rp.setRevokedBy(revokedBy);
              rolePermissionRepository.save(rp);
            });

    // 3. Log to audit
    auditService.logPermissionChange(
        PermissionChangeEvent.cascadingRevocation("role", roleId, revokedBy));

    // 4. Invalidate affected users' caches
    affectedUserIds.forEach(permissionCacheService::invalidateCache);
  }

  /**
   * Revokes all role-permission grants for a permission when the permission is soft-deleted.
   *
   * @param permissionId the permission ID
   * @param revokedBy the user performing the revocation
   */
  public void revokeAllForPermission(String permissionId, String revokedBy) {
    var now = Instant.now();

    // 1. Revoke all RolePermission entries for permission
    var affectedRoleIds = new HashSet<String>();
    rolePermissionRepository
        .findActiveByPermissionId(permissionId)
        .forEach(
            rp -> {
              rp.setRevokedAt(now);
              rp.setRevokedBy(revokedBy);
              rolePermissionRepository.save(rp);
              affectedRoleIds.add(rp.getRoleId());
            });

    // 2. Log to audit
    auditService.logPermissionChange(
        PermissionChangeEvent.cascadingRevocation("permission", permissionId, revokedBy));

    // 3. Invalidate affected users' caches (via roles)
    affectedRoleIds.forEach(
        roleId ->
            userRoleRepository
                .findActiveByRoleId(roleId)
                .forEach(ur -> permissionCacheService.invalidateCache(ur.getUserId())));
  }
}
