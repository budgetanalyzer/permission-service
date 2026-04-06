package org.budgetanalyzer.permission.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.repository.UserRoleRepository;
import org.budgetanalyzer.permission.service.dto.EffectivePermissions;

/**
 * Core service for permission queries.
 *
 * <p>Resolves effective permissions for a user based on role assignments.
 */
@Service
@Transactional(readOnly = true)
public class PermissionService {

  private final UserRoleRepository userRoleRepository;

  /**
   * Constructs a PermissionService with the required repository.
   *
   * @param userRoleRepository the user-role join table repository
   */
  public PermissionService(UserRoleRepository userRoleRepository) {
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
}
