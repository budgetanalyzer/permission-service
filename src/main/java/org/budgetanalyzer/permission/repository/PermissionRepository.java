package org.budgetanalyzer.permission.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.budgetanalyzer.core.repository.SoftDeleteOperations;
import org.budgetanalyzer.permission.domain.Permission;

/**
 * Repository for Permission entities with soft-delete support.
 *
 * <p>Provides methods to query active (non-deleted) permissions and supports filtering by resource
 * type for permission management.
 */
@Repository
public interface PermissionRepository
    extends JpaRepository<Permission, String>, SoftDeleteOperations<Permission> {

  /**
   * Finds an active permission by its ID.
   *
   * @param id the permission ID
   * @return the permission if found and not deleted
   */
  Optional<Permission> findByIdAndDeletedFalse(String id);

  /**
   * Finds all active permissions for a specific resource type.
   *
   * @param resourceType the resource type (e.g., "transactions", "accounts")
   * @return list of active permissions for the resource type
   */
  List<Permission> findByResourceTypeAndDeletedFalse(String resourceType);

  /**
   * Finds all active permissions.
   *
   * @return list of all non-deleted permissions
   */
  List<Permission> findAllByDeletedFalse();
}
