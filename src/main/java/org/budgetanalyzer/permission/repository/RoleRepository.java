package org.budgetanalyzer.permission.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.budgetanalyzer.core.repository.SoftDeleteOperations;
import org.budgetanalyzer.permission.domain.Role;

/**
 * Repository for Role entities with soft-delete support.
 *
 * <p>Provides methods to query active (non-deleted) roles and supports hierarchical role queries
 * through parent role relationships.
 */
@Repository
public interface RoleRepository extends JpaRepository<Role, String>, SoftDeleteOperations<Role> {

  /**
   * Finds an active role by its ID.
   *
   * @param id the role ID
   * @return the role if found and not deleted
   */
  Optional<Role> findByIdAndDeletedFalse(String id);

  /**
   * Finds all active child roles of a parent role.
   *
   * @param parentRoleId the parent role ID
   * @return list of active child roles
   */
  List<Role> findByParentRoleIdAndDeletedFalse(String parentRoleId);

  /**
   * Finds all active roles.
   *
   * @return list of all non-deleted roles
   */
  List<Role> findAllByDeletedFalse();
}
