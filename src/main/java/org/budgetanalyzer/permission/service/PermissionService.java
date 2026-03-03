package org.budgetanalyzer.permission.service;

import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.domain.Role;
import org.budgetanalyzer.permission.domain.UserRole;
import org.budgetanalyzer.permission.repository.RoleRepository;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;
import org.budgetanalyzer.permission.service.dto.EffectivePermissions;
import org.budgetanalyzer.permission.service.exception.DuplicateRoleAssignmentException;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

/**
 * Core service for permission operations.
 *
 * <p>Handles role assignments and permission queries.
 */
@Service
@Transactional(readOnly = true)
public class PermissionService {

  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;
  private final RoleRepository roleRepository;

  /**
   * Constructs a PermissionService with the required repositories.
   *
   * @param userRepository the user repository
   * @param userRoleRepository the user-role join table repository
   * @param roleRepository the role repository
   */
  public PermissionService(
      UserRepository userRepository,
      UserRoleRepository userRoleRepository,
      RoleRepository roleRepository) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
    this.roleRepository = roleRepository;
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
   * Gets all active roles for a user.
   *
   * @param userId the user ID
   * @return list of roles
   */
  public List<Role> getUserRoles(String userId) {
    var userRoles = userRoleRepository.findByUserId(userId);
    return userRoles.stream()
        .map(ur -> roleRepository.findByIdAndDeletedFalse(ur.getRoleId()))
        .filter(Optional::isPresent)
        .map(Optional::get)
        .toList();
  }

  /**
   * Assigns a role to a user.
   *
   * @param userId the user ID to assign the role to
   * @param roleId the role ID to assign
   */
  @Transactional
  public void assignRole(String userId, String roleId) {
    userRepository
        .findByIdAndDeletedFalse(userId)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

    roleRepository
        .findByIdAndDeletedFalse(roleId)
        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + roleId));

    if (userRoleRepository.findByUserIdAndRoleId(userId, roleId).isPresent()) {
      throw new DuplicateRoleAssignmentException(userId, roleId);
    }

    var userRole = new UserRole();
    userRole.setUserId(userId);
    userRole.setRoleId(roleId);
    userRoleRepository.save(userRole);
  }

  /**
   * Revokes a role from a user (hard delete).
   *
   * @param userId the user ID to revoke the role from
   * @param roleId the role ID to revoke
   */
  @Transactional
  public void revokeRole(String userId, String roleId) {
    userRoleRepository
        .findByUserIdAndRoleId(userId, roleId)
        .orElseThrow(() -> new ResourceNotFoundException("Role assignment not found"));

    userRoleRepository.deleteByUserIdAndRoleId(userId, roleId);
  }
}
