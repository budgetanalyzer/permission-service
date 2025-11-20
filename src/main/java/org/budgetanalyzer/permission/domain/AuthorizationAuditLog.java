package org.budgetanalyzer.permission.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Immutable audit log entry for authorization decisions and permission changes.
 *
 * <p>This entity does not extend any base class as it is immutable and should never be updated or
 * deleted. Each entry represents a single authorization event that occurred in the system.
 */
@Entity
@Table(name = "authorization_audit_log")
public class AuthorizationAuditLog {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "timestamp", nullable = false)
  private Instant timestamp;

  @Column(name = "user_id", length = 50)
  private String userId;

  @Column(name = "action", nullable = false)
  private String action;

  @Column(name = "resource_type", length = 50)
  private String resourceType;

  @Column(name = "resource_id")
  private String resourceId;

  @Column(name = "decision", nullable = false, length = 20)
  private String decision;

  @Column(name = "reason", length = 500)
  private String reason;

  @Column(name = "ip_address", length = 45)
  private String ipAddress;

  @Column(name = "user_agent")
  private String userAgent;

  public AuthorizationAuditLog() {}

  /**
   * Creates a new audit log entry with the current timestamp.
   *
   * @param userId the user who performed or triggered the action
   * @param action the action performed (e.g., ROLE_ASSIGNED, ACCESS_DENIED)
   * @param resourceType the type of resource affected
   * @param resourceId the ID of the resource affected
   * @param decision the authorization decision (GRANTED, DENIED)
   * @param reason the reason for the decision
   */
  public AuthorizationAuditLog(
      String userId,
      String action,
      String resourceType,
      String resourceId,
      String decision,
      String reason) {
    this.timestamp = Instant.now();
    this.userId = userId;
    this.action = action;
    this.resourceType = resourceType;
    this.resourceId = resourceId;
    this.decision = decision;
    this.reason = reason;
  }

  public Long getId() {
    return id;
  }

  public Instant getTimestamp() {
    return timestamp;
  }

  public String getUserId() {
    return userId;
  }

  public String getAction() {
    return action;
  }

  public String getResourceType() {
    return resourceType;
  }

  public String getResourceId() {
    return resourceId;
  }

  public String getDecision() {
    return decision;
  }

  public String getReason() {
    return reason;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }
}
