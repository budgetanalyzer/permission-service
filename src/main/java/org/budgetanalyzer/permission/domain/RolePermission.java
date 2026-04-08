package org.budgetanalyzer.permission.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.budgetanalyzer.core.domain.AuditableEntity;

/**
 * Represents a permission granted to a role. Simple join table entity.
 *
 * <p>Extends {@link AuditableEntity} (not {@link
 * org.budgetanalyzer.core.domain.SoftDeletableEntity}) because no revocation flow exists: the row's
 * presence is the grant. When a revocation flow is added, revisit this choice — see {@code
 * docs/authorization-model.md} → "Deferred: grant/revocation audit trail".
 */
@Entity
@Table(name = "role_permissions")
public class RolePermission extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "role_id", nullable = false, length = 50)
  private String roleId;

  @Column(name = "permission_id", nullable = false, length = 100)
  private String permissionId;

  public RolePermission() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getRoleId() {
    return roleId;
  }

  public void setRoleId(String roleId) {
    this.roleId = roleId;
  }

  public String getPermissionId() {
    return permissionId;
  }

  public void setPermissionId(String permissionId) {
    this.permissionId = permissionId;
  }
}
