package org.budgetanalyzer.permission.repository;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.budgetanalyzer.permission.domain.AuthorizationAuditLog;

/**
 * Repository for AuthorizationAuditLog entities.
 *
 * <p>Provides methods to query immutable audit log entries for authorization decisions and
 * permission changes. Audit logs are never modified or deleted.
 */
@Repository
public interface AuditLogRepository extends JpaRepository<AuthorizationAuditLog, Long> {

  /**
   * Finds all audit log entries for a specific user with pagination.
   *
   * @param userId the user ID
   * @param pageable pagination parameters
   * @return page of audit log entries for the user
   */
  Page<AuthorizationAuditLog> findByUserId(String userId, Pageable pageable);

  /**
   * Finds audit log entries within a timestamp range.
   *
   * <p>Results are ordered by timestamp descending (most recent first).
   *
   * @param start the start of the time range (inclusive)
   * @param end the end of the time range (inclusive)
   * @param pageable pagination parameters
   * @return page of audit log entries within the range
   */
  @Query(
      "SELECT a FROM AuthorizationAuditLog a "
          + "WHERE a.timestamp BETWEEN :start AND :end "
          + "ORDER BY a.timestamp DESC")
  Page<AuthorizationAuditLog> findByTimestampRange(
      @Param("start") Instant start, @Param("end") Instant end, Pageable pageable);
}
