package org.budgetanalyzer.permission.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.domain.Role;
import org.budgetanalyzer.permission.repository.RolePermissionRepository;
import org.budgetanalyzer.permission.repository.RoleRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

/**
 * Service for role CRUD operations.
 *
 * <p>Handles role creation, updates, and soft deletion.
 */
@Service
@Transactional(readOnly = true)
public class RoleService {

  private final RoleRepository roleRepository;
  private final UserRoleRepository userRoleRepository;
  private final RolePermissionRepository rolePermissionRepository;

  /**
   * Constructs a RoleService with the required repositories.
   *
   * @param roleRepository the role repository
   * @param userRoleRepository the user-role join table repository
   * @param rolePermissionRepository the role-permission join table repository
   */
  public RoleService(
      RoleRepository roleRepository,
      UserRoleRepository userRoleRepository,
      RolePermissionRepository rolePermissionRepository) {
    this.roleRepository = roleRepository;
    this.userRoleRepository = userRoleRepository;
    this.rolePermissionRepository = rolePermissionRepository;
  }

  /**
   * Returns all non-deleted roles.
   *
   * @return list of active roles
   */
  public List<Role> getAllRoles() {
    return roleRepository.findAllByDeletedFalse();
  }

  /**
   * Returns a non-deleted role by ID.
   *
   * @param id the role ID
   * @return the role
   * @throws ResourceNotFoundException if the role does not exist
   */
  public Role getRole(String id) {
    return roleRepository
        .findByIdAndDeletedFalse(id)
        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
  }

  /**
   * Creates a new role with a generated ID.
   *
   * @param name the role name
   * @param description the role description
   * @return the persisted role
   */
  @Transactional
  public Role createRole(String name, String description) {
    var role = new Role();
    role.setId(generateRoleId());
    role.setName(name);
    role.setDescription(description);
    return roleRepository.save(role);
  }

  /**
   * Updates the name and description of an existing role.
   *
   * @param id the role ID
   * @param name the new name
   * @param description the new description
   * @return the updated role
   */
  @Transactional
  public Role updateRole(String id, String name, String description) {
    var role = getRole(id);
    role.setName(name);
    role.setDescription(description);
    return roleRepository.save(role);
  }

  /**
   * Soft-deletes a role and removes all user and permission associations.
   *
   * @param id the role ID
   * @param deletedBy the user ID performing the deletion
   */
  @Transactional
  public void deleteRole(String id, String deletedBy) {
    var role = getRole(id);

    userRoleRepository.deleteByRoleId(id);
    rolePermissionRepository.deleteByRoleId(id);

    role.markDeleted(deletedBy);
    roleRepository.save(role);
  }

  private String generateRoleId() {
    return "role_" + UUID.randomUUID().toString().replace("-", "");
  }
}
