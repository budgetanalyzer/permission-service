package org.budgetanalyzer.permission.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.event.PermissionChangeEvent;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

/**
 * Service for user management with soft delete support.
 *
 * <p>Handles user queries and soft deletion with cascading revocation.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final CascadingRevocationService cascadingRevocationService;
  private final AuditService auditService;

  /**
   * Constructs a new UserService.
   *
   * @param userRepository the user repository
   * @param cascadingRevocationService the cascading revocation service
   * @param auditService the audit service
   */
  public UserService(
      UserRepository userRepository,
      CascadingRevocationService cascadingRevocationService,
      AuditService auditService) {
    this.userRepository = userRepository;
    this.cascadingRevocationService = cascadingRevocationService;
    this.auditService = auditService;
  }

  /**
   * Gets a user by ID.
   *
   * @param id the user ID
   * @return the user
   * @throws ResourceNotFoundException if user not found
   */
  public User getUser(String id) {
    return userRepository
        .findByIdAndDeletedFalse(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
  }

  /**
   * Gets all active users.
   *
   * @return list of all non-deleted users
   */
  public List<User> getAllUsers() {
    return userRepository.findAllActive();
  }

  /**
   * Soft deletes a user and cascades revocation to all permissions.
   *
   * @param id the user ID
   * @param deletedBy the user performing the deletion
   */
  @Transactional
  public void deleteUser(String id, String deletedBy) {
    // 1. Find user (must not already be deleted)
    var user = getUser(id);

    // 2. Call cascading revocation
    cascadingRevocationService.revokeAllForUser(id, deletedBy);

    // 3. Soft delete the user
    user.markDeleted(deletedBy);
    userRepository.save(user);

    // 4. Log to audit
    auditService.logPermissionChange(PermissionChangeEvent.userDeleted(user, deletedBy));
  }

  /**
   * Restores a soft-deleted user.
   *
   * <p>Note: Does NOT restore revoked assignments - must be re-granted.
   *
   * @param id the user ID
   */
  @Transactional
  public void restoreUser(String id) {
    var user =
        userRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));

    if (!user.isDeleted()) {
      throw new IllegalStateException("User is not deleted");
    }

    user.restore();
    userRepository.save(user);

    auditService.logPermissionChange(PermissionChangeEvent.userRestored(user));
  }
}
