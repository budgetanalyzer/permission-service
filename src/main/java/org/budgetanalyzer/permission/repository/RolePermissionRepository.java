package org.budgetanalyzer.permission.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.budgetanalyzer.permission.domain.RolePermission;

/**
 * Repository for RolePermission entities (temporal permission grants to roles).
 *
 * <p>This repository supports temporal queries for managing which permissions are granted to roles.
 * Permission grants are never deleted; revocation is tracked via the revokedAt timestamp.
 */
@Repository
public interface RolePermissionRepository extends JpaRepository<RolePermission, Long> {

  /**
   * Finds all active permission grants for a role.
   *
   * @param roleId the role ID
   * @return list of active (non-revoked) permission grants
   */
  List<RolePermission> findByRoleIdAndRevokedAtIsNull(String roleId);

  /**
   * Finds all active permission grants for a role (for cascading revocation when role is
   * soft-deleted).
   *
   * @param roleId the role ID
   * @return list of active permission grants
   */
  @Query("SELECT rp FROM RolePermission rp WHERE rp.roleId = :roleId AND rp.revokedAt IS NULL")
  List<RolePermission> findActiveByRoleId(@Param("roleId") String roleId);

  /**
   * Finds all active grants of a specific permission (for cascading revocation when permission is
   * soft-deleted).
   *
   * @param permissionId the permission ID
   * @return list of active grants of this permission
   */
  @Query(
      "SELECT rp FROM RolePermission rp "
          + "WHERE rp.permissionId = :permissionId "
          + "AND rp.revokedAt IS NULL")
  List<RolePermission> findActiveByPermissionId(@Param("permissionId") String permissionId);
}
