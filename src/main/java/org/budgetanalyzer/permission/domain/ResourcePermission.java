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
 * Represents a permission granted to a user for a specific resource.
 *
 * <p>This enables fine-grained access control by granting permissions on specific resource
 * instances (e.g., a specific account) rather than all resources of a type. This is a temporal
 * entity; revocation is tracked via the revokedAt timestamp.
 */
@Entity
@Table(name = "resource_permissions")
public class ResourcePermission extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "user_id", nullable = false, length = 50)
  private String userId;

  @Column(name = "resource_type", nullable = false, length = 50)
  private String resourceType;

  @Column(name = "resource_id", nullable = false)
  private String resourceId;

  @Column(name = "permission", nullable = false, length = 50)
  private String permission;

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

  @Column(name = "reason", length = 500)
  private String reason;

  public ResourcePermission() {}

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

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public String getResourceId() {
    return resourceId;
  }

  public void setResourceId(String resourceId) {
    this.resourceId = resourceId;
  }

  public String getPermission() {
    return permission;
  }

  public void setPermission(String permission) {
    this.permission = permission;
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

  public String getReason() {
    return reason;
  }

  public void setReason(String reason) {
    this.reason = reason;
  }

  /**
   * Checks if this resource permission is currently active.
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
   * Revokes this resource permission.
   *
   * @param revokedBy the user ID who is revoking this permission
   */
  public void revoke(String revokedBy) {
    this.revokedAt = Instant.now();
    this.revokedBy = revokedBy;
  }
}
