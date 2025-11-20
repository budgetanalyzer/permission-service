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
public interface UserRepository extends JpaRepository<User, String>, SoftDeleteOperations<User> {

  // Active user queries (filter by deleted = false)

  /**
   * Finds an active user by their ID.
   *
   * @param id the user ID
   * @return the user if found and not deleted
   */
  Optional<User> findByIdAndDeletedFalse(String id);

  /**
   * Finds an active user by their Auth0 subject identifier.
   *
   * @param auth0Sub the Auth0 subject identifier
   * @return the user if found and not deleted
   */
  Optional<User> findByAuth0SubAndDeletedFalse(String auth0Sub);

  /**
   * Finds an active user by their email address.
   *
   * @param email the email address
   * @return the user if found and not deleted
   */
  Optional<User> findByEmailAndDeletedFalse(String email);

  // Include deleted for admin/audit purposes

  /**
   * Finds a user by their Auth0 subject identifier, including soft-deleted users.
   *
   * @param auth0Sub the Auth0 subject identifier
   * @return the user if found (may be deleted)
   */
  Optional<User> findByAuth0Sub(String auth0Sub);
}
