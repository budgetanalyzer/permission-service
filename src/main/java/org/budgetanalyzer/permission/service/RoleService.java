package org.budgetanalyzer.permission.service;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.domain.Role;
import org.budgetanalyzer.permission.repository.RoleRepository;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

/**
 * Service for role CRUD operations.
 *
 * <p>Handles role creation, updates, and soft deletion with cascading revocation.
 */
@Service
@Transactional(readOnly = true)
public class RoleService {

  private final RoleRepository roleRepository;
  private final CascadingRevocationService cascadingRevocationService;

  /**
   * Constructs a new RoleService.
   *
   * @param roleRepository the role repository
   * @param cascadingRevocationService the cascading revocation service
   */
  public RoleService(
      RoleRepository roleRepository, CascadingRevocationService cascadingRevocationService) {
    this.roleRepository = roleRepository;
    this.cascadingRevocationService = cascadingRevocationService;
  }

  /**
   * Gets all active roles.
   *
   * @return list of all non-deleted roles
   */
  public List<Role> getAllRoles() {
    return roleRepository.findAllByDeletedFalse();
  }

  /**
   * Gets a role by ID.
   *
   * @param id the role ID
   * @return the role
   * @throws ResourceNotFoundException if role not found
   */
  public Role getRole(String id) {
    return roleRepository
        .findByIdAndDeletedFalse(id)
        .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));
  }

  /**
   * Creates a new role.
   *
   * @param name the role name
   * @param description the role description
   * @param parentRoleId optional parent role ID for inheritance
   * @return the created role
   */
  @Transactional
  public Role createRole(String name, String description, String parentRoleId) {
    var role = new Role();
    role.setId(generateRoleId());
    role.setName(name);
    role.setDescription(description);
    role.setParentRoleId(parentRoleId);
    return roleRepository.save(role);
  }

  /**
   * Updates an existing role.
   *
   * @param id the role ID
   * @param name the new name
   * @param description the new description
   * @param parentRoleId the new parent role ID
   * @return the updated role
   */
  @Transactional
  public Role updateRole(String id, String name, String description, String parentRoleId) {
    var role = getRole(id);
    role.setName(name);
    role.setDescription(description);
    role.setParentRoleId(parentRoleId);
    return roleRepository.save(role);
  }

  /**
   * Soft deletes a role and cascades revocation to all assignments.
   *
   * @param id the role ID
   * @param deletedBy the user performing the deletion
   */
  @Transactional
  public void deleteRole(String id, String deletedBy) {
    // 1. Find role (must not already be deleted)
    var role = getRole(id);

    // 2. Call cascading revocation
    cascadingRevocationService.revokeAllForRole(id, deletedBy);

    // 3. Soft delete the role
    role.markDeleted(deletedBy);
    roleRepository.save(role);
  }

  /**
   * Restores a soft-deleted role.
   *
   * <p>Note: Does NOT restore revoked UserRole/RolePermission entries.
   *
   * @param id the role ID
   */
  @Transactional
  public void restoreRole(String id) {
    var role =
        roleRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Role not found: " + id));

    if (!role.isDeleted()) {
      throw new IllegalStateException("Role is not deleted");
    }

    role.restore();
    roleRepository.save(role);
  }

  private String generateRoleId() {
    return "role_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}
