package org.budgetanalyzer.permission.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.budgetanalyzer.core.domain.AuditableEntity;

/**
 * Represents a permission granted to a role.
 *
 * <p>This is a temporal entity that tracks when permissions were granted to roles and when they
 * were revoked. Records are never deleted; revocation is tracked via the revokedAt timestamp.
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

  @Column(name = "granted_at", nullable = false)
  private Instant grantedAt;

  @Column(name = "granted_by", length = 50)
  private String grantedBy;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_by", length = 50)
  private String revokedBy;

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

  public Instant getGrantedAt() {
    return grantedAt;
  }

  public void setGrantedAt(Instant grantedAt) {
    this.grantedAt = grantedAt;
  }

  public String getGrantedBy() {
    return grantedBy;
  }

  public void setGrantedBy(String grantedBy) {
    this.grantedBy = grantedBy;
  }

  public Instant getRevokedAt() {
    return revokedAt;
  }

  public void setRevokedAt(Instant revokedAt) {
    this.revokedAt = revokedAt;
  }

  public String getRevokedBy() {
    return revokedBy;
  }

  public void setRevokedBy(String revokedBy) {
    this.revokedBy = revokedBy;
  }

  /**
   * Checks if this permission grant is currently active.
   *
   * @return true if not revoked
   */
  public boolean isActive() {
    return revokedAt == null;
  }

  /**
   * Revokes this permission grant.
   *
   * @param revokedBy the user ID who is revoking this grant
   */
  public void revoke(String revokedBy) {
    this.revokedAt = Instant.now();
    this.revokedBy = revokedBy;
  }
}
