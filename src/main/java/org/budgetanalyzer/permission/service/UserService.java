package org.budgetanalyzer.permission.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

/**
 * Service for user management with soft delete support.
 *
 * <p>Handles user queries and soft deletion.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;

  /**
   * Constructs a UserService with the required repositories.
   *
   * @param userRepository the user repository
   * @param userRoleRepository the user-role join table repository
   */
  public UserService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
  }

  /**
   * Returns a non-deleted user by ID.
   *
   * @param id the user ID
   * @return the user
   * @throws ResourceNotFoundException if the user does not exist
   */
  public User getUser(String id) {
    return userRepository
        .findByIdAndDeletedFalse(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
  }

  /**
   * Soft-deletes a user and removes all role assignments.
   *
   * @param id the user ID
   * @param deletedBy the user ID performing the deletion
   */
  @Transactional
  public void deleteUser(String id, String deletedBy) {
    var user = getUser(id);

    userRoleRepository.deleteByUserId(id);

    user.markDeleted(deletedBy);
    userRepository.save(user);
  }
}
