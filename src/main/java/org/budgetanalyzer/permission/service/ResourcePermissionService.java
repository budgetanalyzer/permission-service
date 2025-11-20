package org.budgetanalyzer.permission.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.domain.ResourcePermission;
import org.budgetanalyzer.permission.event.PermissionChangeEvent;
import org.budgetanalyzer.permission.repository.ResourcePermissionRepository;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

/**
 * Service for resource-specific permission management.
 *
 * <p>Handles granting and revoking fine-grained permissions on specific resources.
 */
@Service
@Transactional(readOnly = true)
public class ResourcePermissionService {

  private final ResourcePermissionRepository resourcePermissionRepository;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final PermissionCacheService permissionCacheService;

  /**
   * Constructs a new ResourcePermissionService.
   *
   * @param resourcePermissionRepository the resource permission repository
   * @param userRepository the user repository
   * @param auditService the audit service
   * @param permissionCacheService the permission cache service
   */
  public ResourcePermissionService(
      ResourcePermissionRepository resourcePermissionRepository,
      UserRepository userRepository,
      AuditService auditService,
      PermissionCacheService permissionCacheService) {
    this.resourcePermissionRepository = resourcePermissionRepository;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.permissionCacheService = permissionCacheService;
  }

  /**
   * Grants a permission on a specific resource to a user.
   *
   * @param userId the user ID
   * @param resourceType the resource type
   * @param resourceId the resource ID
   * @param permission the permission to grant
   * @param expiresAt optional expiration time
   * @param reason the reason for granting
   * @param grantedBy the user performing the grant
   * @return the created resource permission
   */
  @Transactional
  public ResourcePermission grantPermission(
      String userId,
      String resourceType,
      String resourceId,
      String permission,
      Instant expiresAt,
      String reason,
      String grantedBy) {
    // Validate user exists
    userRepository
        .findByIdAndDeletedFalse(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

    // Create resource permission
    var resourcePermission = new ResourcePermission();
    resourcePermission.setUserId(userId);
    resourcePermission.setResourceType(resourceType);
    resourcePermission.setResourceId(resourceId);
    resourcePermission.setPermission(permission);
    resourcePermission.setGrantedAt(Instant.now());
    resourcePermission.setGrantedBy(grantedBy);
    resourcePermission.setExpiresAt(expiresAt);
    resourcePermission.setReason(reason);

    var saved = resourcePermissionRepository.save(resourcePermission);

    // Log and invalidate cache
    auditService.logPermissionChange(PermissionChangeEvent.resourcePermissionGranted(saved));
    permissionCacheService.invalidateCache(userId);

    return saved;
  }

  /**
   * Revokes a resource permission.
   *
   * @param id the resource permission ID
   * @param revokedBy the user performing the revocation
   */
  @Transactional
  public void revokePermission(Long id, String revokedBy) {
    var permission =
        resourcePermissionRepository
            .findById(id)
            .orElseThrow(
                () -> new ResourceNotFoundException("Resource permission not found: " + id));

    permission.setRevokedAt(Instant.now());
    permission.setRevokedBy(revokedBy);
    resourcePermissionRepository.save(permission);

    auditService.logPermissionChange(PermissionChangeEvent.resourcePermissionRevoked(permission));
    permissionCacheService.invalidateCache(permission.getUserId());
  }

  /**
   * Gets all active resource permissions for a user.
   *
   * @param userId the user ID
   * @return list of active resource permissions
   */
  public List<ResourcePermission> getForUser(String userId) {
    return resourcePermissionRepository.findByUserIdAndRevokedAtIsNull(userId);
  }
}
