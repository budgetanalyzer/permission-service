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
 * Represents a role assignment to a user.
 *
 * <p>This is a temporal entity that tracks when roles were granted and revoked. Records are never
 * deleted; revocation is tracked via the revokedAt timestamp. This enables point-in-time queries
 * for auditing and compliance.
 */
@Entity
@Table(name = "user_roles")
public class UserRole extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, length = 50)
  private String userId;

  @Column(name = "role_id", nullable = false, length = 50)
  private String roleId;

  @Column(name = "organization_id", length = 50)
  private String organizationId;

  @Column(name = "granted_at", nullable = false)
  private Instant grantedAt;

  @Column(name = "granted_by", length = 50)
  private String grantedBy;

  @Column(name = "expires_at")
  private Instant expiresAt;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_by", length = 50)
  private String revokedBy;

  public UserRole() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getUserId() {
    return userId;
  }

  public void setUserId(String userId) {
    this.userId = userId;
  }

  public String getRoleId() {
    return roleId;
  }

  public void setRoleId(String roleId) {
    this.roleId = roleId;
  }

  public String getOrganizationId() {
    return organizationId;
  }

  public void setOrganizationId(String organizationId) {
    this.organizationId = organizationId;
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

  public Instant getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(Instant expiresAt) {
    this.expiresAt = expiresAt;
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
   * Checks if this role assignment is currently active.
   *
   * @return true if not revoked and not expired
   */
  public boolean isActive() {
    if (revokedAt != null) {
      return false;
    }
    if (expiresAt != null && expiresAt.isBefore(Instant.now())) {
      return false;
    }
    return true;
  }

  /**
   * Revokes this role assignment.
   *
   * @param revokedBy the user ID who is revoking this assignment
   */
  public void revoke(String revokedBy) {
    this.revokedAt = Instant.now();
    this.revokedBy = revokedBy;
  }
}
