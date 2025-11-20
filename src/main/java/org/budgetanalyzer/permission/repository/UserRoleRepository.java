package org.budgetanalyzer.permission.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.budgetanalyzer.permission.domain.UserRole;

/**
 * Repository for UserRole entities (temporal role assignments).
 *
 * <p>This repository supports temporal queries for auditing and point-in-time permission checks.
 * Role assignments are never deleted; revocation is tracked via the revokedAt timestamp.
 */
@Repository
public interface UserRoleRepository extends JpaRepository<UserRole, Long> {

  /**
   * Finds all active role assignments for a user.
   *
   * @param userId the user ID
   * @return list of active (non-revoked) role assignments
   */
  List<UserRole> findByUserIdAndRevokedAtIsNull(String userId);

  /**
   * Finds a specific active role assignment for a user.
   *
   * @param userId the user ID
   * @param roleId the role ID
   * @return the active role assignment if exists
   */
  Optional<UserRole> findByUserIdAndRoleIdAndRevokedAtIsNull(String userId, String roleId);

  /**
   * Finds roles a user had at a specific point in time (for auditing).
   *
   * @param userId the user ID
   * @param pointInTime the point in time to check
   * @return list of role assignments that were active at that time
   */
  @Query(
      "SELECT ur FROM UserRole ur "
          + "WHERE ur.userId = :userId "
          + "AND ur.grantedAt <= :pointInTime "
          + "AND (ur.revokedAt IS NULL OR ur.revokedAt > :pointInTime)")
  List<UserRole> findRolesAtPointInTime(
      @Param("userId") String userId, @Param("pointInTime") Instant pointInTime);

  /**
   * Gets all active permission IDs for a user through their role assignments.
   *
   * <p>This query considers role-based permissions and filters out expired and revoked assignments.
   *
   * @param userId the user ID
   * @param now the current time for expiration checking
   * @return set of active permission IDs
   */
  @Query(
      "SELECT rp.permissionId FROM UserRole ur "
          + "JOIN RolePermission rp ON ur.roleId = rp.roleId "
          + "WHERE ur.userId = :userId "
          + "AND ur.revokedAt IS NULL "
          + "AND rp.revokedAt IS NULL "
          + "AND (ur.expiresAt IS NULL OR ur.expiresAt > :now)")
  Set<String> findActivePermissionIdsByUserId(
      @Param("userId") String userId, @Param("now") Instant now);

  /**
   * Finds all active role assignments for a user (for cascading revocation).
   *
   * @param userId the user ID
   * @return list of active role assignments
   */
  @Query("SELECT ur FROM UserRole ur WHERE ur.userId = :userId AND ur.revokedAt IS NULL")
  List<UserRole> findActiveByUserId(@Param("userId") String userId);

  /**
   * Finds all active role assignments for a role (for cascading revocation when role is
   * soft-deleted).
   *
   * @param roleId the role ID
   * @return list of active role assignments
   */
  @Query("SELECT ur FROM UserRole ur WHERE ur.roleId = :roleId AND ur.revokedAt IS NULL")
  List<UserRole> findActiveByRoleId(@Param("roleId") String roleId);
}
