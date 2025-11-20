package org.budgetanalyzer.permission.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import org.budgetanalyzer.permission.domain.AuthorizationAuditLog;
import org.budgetanalyzer.permission.event.PermissionChangeEvent;
import org.budgetanalyzer.permission.repository.AuditLogRepository;
import org.budgetanalyzer.permission.service.dto.AuditQueryFilter;

/**
 * Service for audit logging of permission changes and access decisions.
 *
 * <p>Audit log entries are immutable and never modified or deleted.
 */
@Service
public class AuditService {

  private final AuditLogRepository auditLogRepository;

  /**
   * Constructs a new AuditService.
   *
   * @param auditLogRepository the audit log repository
   */
  public AuditService(AuditLogRepository auditLogRepository) {
    this.auditLogRepository = auditLogRepository;
  }

  /**
   * Logs a permission change event asynchronously.
   *
   * @param event the permission change event
   */
  @Async
  public void logPermissionChange(PermissionChangeEvent event) {
    var log =
        new AuthorizationAuditLog(
            event.getUserId(),
            event.getAction(),
            null,
            null,
            "GRANTED",
            contextToString(event.getContext()));
    auditLogRepository.save(log);
  }

  /**
   * Logs an access decision asynchronously.
   *
   * @param userId the user ID
   * @param action the action attempted
   * @param resourceType the resource type
   * @param resourceId the resource ID
   * @param decision the authorization decision (GRANTED, DENIED)
   * @param reason the reason for the decision
   */
  @Async
  public void logAccessDecision(
      String userId,
      String action,
      String resourceType,
      String resourceId,
      String decision,
      String reason) {
    var log = new AuthorizationAuditLog(userId, action, resourceType, resourceId, decision, reason);
    auditLogRepository.save(log);
  }

  /**
   * Queries the audit log with filters.
   *
   * @param filter the query filters
   * @param pageable pagination parameters
   * @return page of audit log entries
   */
  public Page<AuthorizationAuditLog> queryAuditLog(AuditQueryFilter filter, Pageable pageable) {
    if (filter.userId() != null) {
      return auditLogRepository.findByUserId(filter.userId(), pageable);
    }
    if (filter.startTime() != null && filter.endTime() != null) {
      return auditLogRepository.findByTimestampRange(
          filter.startTime(), filter.endTime(), pageable);
    }
    return auditLogRepository.findAll(pageable);
  }

  /**
   * Queries audit log entries for a specific user.
   *
   * @param userId the user ID
   * @param pageable pagination parameters
   * @return page of audit log entries for the user
   */
  public Page<AuthorizationAuditLog> queryByUser(String userId, Pageable pageable) {
    return auditLogRepository.findByUserId(userId, pageable);
  }

  private String contextToString(java.util.Map<String, String> context) {
    if (context == null || context.isEmpty()) {
      return null;
    }
    return context.toString();
  }
}
