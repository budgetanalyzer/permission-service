package org.budgetanalyzer.permission.repository;

import java.time.Instant;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import org.budgetanalyzer.permission.domain.Delegation;

/**
 * Repository for Delegation entities (temporal permission delegations between users).
 *
 * <p>This repository supports temporal queries for managing and querying permission delegations.
 * Delegations are never deleted; revocation is tracked via the revokedAt timestamp.
 */
@Repository
public interface DelegationRepository extends JpaRepository<Delegation, Long> {

  /**
   * Finds all active delegations received by a user.
   *
   * <p>This query returns delegations that are:
   *
   * <ul>
   *   <li>Not revoked
   *   <li>Currently within their validity period (validFrom <= now < validUntil)
   * </ul>
   *
   * @param userId the delegatee user ID
   * @param now the current time for validity checking
   * @return list of active delegations received by the user
   */
  @Query(
      "SELECT d FROM Delegation d "
          + "WHERE d.delegateeId = :userId "
          + "AND d.revokedAt IS NULL "
          + "AND d.validFrom <= :now "
          + "AND (d.validUntil IS NULL OR d.validUntil > :now)")
  List<Delegation> findActiveDelegationsForUser(
      @Param("userId") String userId, @Param("now") Instant now);

  /**
   * Finds all delegations created by a user (including revoked, for audit).
   *
   * @param delegatorId the delegator user ID
   * @return list of all delegations created by the user
   */
  List<Delegation> findByDelegatorId(String delegatorId);

  /**
   * Finds all active delegations created by a user.
   *
   * @param delegatorId the delegator user ID
   * @param now the current time for validity checking
   * @return list of active delegations created by the user
   */
  @Query(
      "SELECT d FROM Delegation d "
          + "WHERE d.delegatorId = :delegatorId "
          + "AND d.revokedAt IS NULL "
          + "AND d.validFrom <= :now "
          + "AND (d.validUntil IS NULL OR d.validUntil > :now)")
  List<Delegation> findByDelegatorIdAndRevokedAtIsNull(
      @Param("delegatorId") String delegatorId, @Param("now") Instant now);

  /**
   * Finds all active delegations involving a user (for cascading revocation when user is
   * soft-deleted).
   *
   * <p>This includes delegations where the user is either the delegator or the delegatee.
   *
   * @param userId the user ID
   * @param now the current time for validity checking
   * @return list of active delegations involving the user
   */
  @Query(
      "SELECT d FROM Delegation d "
          + "WHERE (d.delegatorId = :userId OR d.delegateeId = :userId) "
          + "AND d.revokedAt IS NULL "
          + "AND d.validFrom <= :now "
          + "AND (d.validUntil IS NULL OR d.validUntil > :now)")
  List<Delegation> findActiveByUserId(@Param("userId") String userId, @Param("now") Instant now);
}
