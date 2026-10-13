package org.budgetanalyzer.permission.service;

import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;
import org.budgetanalyzer.permission.service.dto.EffectivePermissions;
import org.budgetanalyzer.permission.service.dto.UserAuthorizationView;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

/**
 * Core service for permission queries.
 *
 * <p>Resolves effective permissions for a user based on role assignments.
 */
@Service
@Transactional(readOnly = true)
public class PermissionService {

  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;

  /**
   * Constructs a PermissionService with the required repository.
   *
   * @param userRepository the user repository
   * @param userRoleRepository the user-role join table repository
   */
  public PermissionService(UserRepository userRepository, UserRoleRepository userRoleRepository) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
  }

  /**
   * Gets the effective permissions for a user.
   *
   * @param userId the user ID
   * @return the effective permissions (roles and permissions)
   */
  public EffectivePermissions getEffectivePermissions(String userId) {
    var roles = userRoleRepository.findRoleIdsByUserId(userId);
    var permissions = userRoleRepository.findPermissionIdsByUserId(userId);
    return new EffectivePermissions(roles, permissions);
  }

  /**
   * Gets the current account status and effective authorization grants for a non-deleted user.
   *
   * <p>Deactivated users return no roles or permissions even if stale assignments remain.
   *
   * @param userId the internal user ID
   * @return the current user authorization view
   * @throws ResourceNotFoundException if the user does not exist or is soft-deleted
   */
  public UserAuthorizationView getUserAuthorization(String userId) {
    var user =
        userRepository
            .findByIdNotDeleted(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

    if (user.isDeactivated()) {
      return new UserAuthorizationView(user.getId(), user.getStatus(), Set.of(), Set.of());
    }

    var effectivePermissions = getEffectivePermissions(userId);
    return new UserAuthorizationView(
        user.getId(),
        user.getStatus(),
        effectivePermissions.roles(),
        effectivePermissions.permissions());
  }
}
