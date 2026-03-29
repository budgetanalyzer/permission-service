package org.budgetanalyzer.permission.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import org.budgetanalyzer.core.repository.SoftDeleteOperations;
import org.budgetanalyzer.permission.domain.User;

/**
 * Repository for User entities with soft-delete support.
 *
 * <p>Provides methods to query active (non-deleted) users and includes methods for both active-only
 * and all-inclusive queries for admin/audit purposes.
 */
@Repository
public interface UserRepository
    extends JpaRepository<User, String>, SoftDeleteOperations<User, String> {

  // Active user queries (filter by deleted = false)

  /**
   * Finds an active user by their identity provider subject identifier.
   *
   * @param idpSub the identity provider subject identifier
   * @return the user if found and not deleted
   */
  Optional<User> findByIdpSubAndDeletedFalse(String idpSub);

  /**
   * Finds an active user by their email address.
   *
   * @param email the email address
   * @return the user if found and not deleted
   */
  Optional<User> findByEmailAndDeletedFalse(String email);
}
