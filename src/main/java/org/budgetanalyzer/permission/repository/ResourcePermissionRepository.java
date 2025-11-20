package org.budgetanalyzer.permission.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.budgetanalyzer.permission.domain.ResourcePermission;

/**
 * Repository for ResourcePermission entities (temporal resource-specific permissions).
 *
 * <p>This repository supports temporal and fine-grained access control queries for specific
 * resource instances. Permissions are never deleted; revocation is tracked via the revokedAt
 * timestamp.
 */
@Repository
public interface ResourcePermissionRepository extends JpaRepository<ResourcePermission, Long> {

  /**
   * Finds all active resource permissions for a user.
   *
   * @param userId the user ID
   * @return list of active (non-revoked) resource permissions
   */
  List<ResourcePermission> findByUserIdAndRevokedAtIsNull(String userId);

  /**
   * Finds active permissions for a user on a specific resource.
   *
   * <p>This query filters by resource type and ID, and excludes expired and revoked permissions.
   *
   * @param userId the user ID
   * @param resourceType the resource type (e.g., "account", "transaction")
   * @param resourceId the specific resource ID
   * @param now the current time for expiration checking
   * @return list of active permissions for the resource
   */
  @Query(
      "SELECT rp FROM ResourcePermission rp "
          + "WHERE rp.userId = :userId "
          + "AND rp.resourceType = :resourceType "
          + "AND rp.resourceId = :resourceId "
          + "AND rp.revokedAt IS NULL "
          + "AND (rp.expiresAt IS NULL OR rp.expiresAt > :now)")
  List<ResourcePermission> findActivePermissions(
      @Param("userId") String userId,
      @Param("resourceType") String resourceType,
      @Param("resourceId") String resourceId,
      @Param("now") Instant now);

  /**
   * Finds resource permissions a user had at a specific point in time (for auditing).
   *
   * @param userId the user ID
   * @param pointInTime the point in time to check
   * @return list of resource permissions that were active at that time
   */
  @Query(
      "SELECT rp FROM ResourcePermission rp "
          + "WHERE rp.userId = :userId "
          + "AND rp.grantedAt <= :pointInTime "
          + "AND (rp.revokedAt IS NULL OR rp.revokedAt > :pointInTime)")
  List<ResourcePermission> findPermissionsAtPointInTime(
      @Param("userId") String userId, @Param("pointInTime") Instant pointInTime);

  /**
   * Finds all active resource permissions for a user (for cascading revocation when user is
   * soft-deleted).
   *
   * @param userId the user ID
   * @return list of active resource permissions
   */
  @Query("SELECT rp FROM ResourcePermission rp WHERE rp.userId = :userId AND rp.revokedAt IS NULL")
  List<ResourcePermission> findActiveByUserId(@Param("userId") String userId);
}
