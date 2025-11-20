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
 * Represents a delegation of permissions from one user to another.
 *
 * <p>Delegations allow users to temporarily grant their permissions to others, with configurable
 * scope (full, read_only, transactions_only) and optional resource restrictions. This is a temporal
 * entity; revocation is tracked via the revokedAt timestamp.
 */
@Entity
@Table(name = "delegations")
public class Delegation extends AuditableEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "delegator_id", nullable = false, length = 50)
  private String delegatorId;

  @Column(name = "delegatee_id", nullable = false, length = 50)
  private String delegateeId;

  @Column(name = "scope", nullable = false, length = 50)
  private String scope;

  @Column(name = "resource_type", length = 50)
  private String resourceType;

  @Column(name = "resource_ids", columnDefinition = "jsonb")
  private String[] resourceIds;

  @Column(name = "valid_from", nullable = false)
  private Instant validFrom;

  @Column(name = "valid_until")
  private Instant validUntil;

  @Column(name = "revoked_at")
  private Instant revokedAt;

  @Column(name = "revoked_by", length = 50)
  private String revokedBy;

  public Delegation() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getDelegatorId() {
    return delegatorId;
  }

  public void setDelegatorId(String delegatorId) {
    this.delegatorId = delegatorId;
  }

  public String getDelegateeId() {
    return delegateeId;
  }

  public void setDelegateeId(String delegateeId) {
    this.delegateeId = delegateeId;
  }

  public String getScope() {
    return scope;
  }

  public void setScope(String scope) {
    this.scope = scope;
  }

  public String getResourceType() {
    return resourceType;
  }

  public void setResourceType(String resourceType) {
    this.resourceType = resourceType;
  }

  public String[] getResourceIds() {
    return resourceIds;
  }

  public void setResourceIds(String[] resourceIds) {
    this.resourceIds = resourceIds;
  }

  public Instant getValidFrom() {
    return validFrom;
  }

  public void setValidFrom(Instant validFrom) {
    this.validFrom = validFrom;
  }

  public Instant getValidUntil() {
    return validUntil;
  }

  public void setValidUntil(Instant validUntil) {
    this.validUntil = validUntil;
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
   * Checks if this delegation is currently active.
   *
   * @return true if not revoked, within validity period
   */
  public boolean isActive() {
    if (revokedAt != null) {
      return false;
    }
    Instant now = Instant.now();
    if (validFrom.isAfter(now)) {
      return false;
    }
    if (validUntil != null && validUntil.isBefore(now)) {
      return false;
    }
    return true;
  }

  /**
   * Revokes this delegation.
   *
   * @param revokedBy the user ID who is revoking this delegation
   */
  public void revoke(String revokedBy) {
    this.revokedAt = Instant.now();
    this.revokedBy = revokedBy;
  }
}
