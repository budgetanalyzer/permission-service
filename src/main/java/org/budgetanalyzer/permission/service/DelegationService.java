package org.budgetanalyzer.permission.service;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.domain.Delegation;
import org.budgetanalyzer.permission.event.PermissionChangeEvent;
import org.budgetanalyzer.permission.repository.DelegationRepository;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.service.dto.DelegationsSummary;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

/**
 * Service for delegation management.
 *
 * <p>Handles creating, revoking, and querying permission delegations between users.
 */
@Service
@Transactional(readOnly = true)
public class DelegationService {

  private final DelegationRepository delegationRepository;
  private final UserRepository userRepository;
  private final AuditService auditService;
  private final PermissionCacheService permissionCacheService;

  /**
   * Constructs a new DelegationService.
   *
   * @param delegationRepository the delegation repository
   * @param userRepository the user repository
   * @param auditService the audit service
   * @param permissionCacheService the permission cache service
   */
  public DelegationService(
      DelegationRepository delegationRepository,
      UserRepository userRepository,
      AuditService auditService,
      PermissionCacheService permissionCacheService) {
    this.delegationRepository = delegationRepository;
    this.userRepository = userRepository;
    this.auditService = auditService;
    this.permissionCacheService = permissionCacheService;
  }

  /**
   * Creates a new delegation.
   *
   * @param delegatorId the user granting the delegation
   * @param delegateeId the user receiving the delegation
   * @param scope the delegation scope (full, read_only, transactions_only)
   * @param resourceType optional resource type restriction
   * @param resourceIds optional resource ID restrictions
   * @param validUntil optional expiration time
   * @return the created delegation
   */
  @Transactional
  public Delegation createDelegation(
      String delegatorId,
      String delegateeId,
      String scope,
      String resourceType,
      String[] resourceIds,
      Instant validUntil) {
    // 1. Validate delegatee exists
    userRepository
        .findByIdAndDeletedFalse(delegateeId)
        .orElseThrow(() -> new ResourceNotFoundException("Delegatee not found"));

    // 2. Create delegation with time bounds
    var delegation = new Delegation();
    delegation.setDelegatorId(delegatorId);
    delegation.setDelegateeId(delegateeId);
    delegation.setScope(scope);
    delegation.setResourceType(resourceType);
    delegation.setResourceIds(resourceIds);
    delegation.setValidFrom(Instant.now());
    delegation.setValidUntil(validUntil);

    var saved = delegationRepository.save(delegation);

    // 3. Log to audit
    auditService.logPermissionChange(PermissionChangeEvent.delegationCreated(saved));

    // 4. Invalidate delegatee's permission cache
    permissionCacheService.invalidateCache(delegateeId);

    return saved;
  }

  /**
   * Revokes a delegation.
   *
   * @param id the delegation ID
   * @param revokedBy the user performing the revocation
   */
  @Transactional
  public void revokeDelegation(Long id, String revokedBy) {
    var delegation =
        delegationRepository
            .findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Delegation not found: " + id));

    // 1. Mark delegation as revoked
    delegation.setRevokedAt(Instant.now());
    delegation.setRevokedBy(revokedBy);
    delegationRepository.save(delegation);

    // 2. Log to audit
    auditService.logPermissionChange(PermissionChangeEvent.delegationRevoked(delegation));

    // 3. Invalidate cache
    permissionCacheService.invalidateCache(delegation.getDelegateeId());
  }

  /**
   * Gets all delegations for a user (both given and received).
   *
   * @param userId the user ID
   * @return summary of delegations
   */
  public DelegationsSummary getDelegationsForUser(String userId) {
    var now = Instant.now();
    var given = delegationRepository.findByDelegatorIdAndRevokedAtIsNull(userId, now);
    var received = delegationRepository.findActiveDelegationsForUser(userId, now);

    return new DelegationsSummary(given, received);
  }

  /**
   * Checks if a user has delegated access to a resource.
   *
   * @param delegateeId the user to check
   * @param resourceType the resource type
   * @param resourceId the resource ID
   * @param permission the required permission
   * @return true if delegated access exists
   */
  public boolean hasDelegatedAccess(
      String delegateeId, String resourceType, String resourceId, String permission) {
    var delegations = delegationRepository.findActiveDelegationsForUser(delegateeId, Instant.now());

    return delegations.stream()
        .anyMatch(d -> matchesDelegation(d, resourceType, resourceId, permission));
  }

  private boolean matchesDelegation(
      Delegation delegation, String resourceType, String resourceId, String permission) {
    // Check if delegation covers this resource
    if (delegation.getResourceType() != null
        && !delegation.getResourceType().equals(resourceType)) {
      return false;
    }

    if (delegation.getResourceIds() != null && delegation.getResourceIds().length > 0) {
      boolean found = false;
      for (String id : delegation.getResourceIds()) {
        if (id.equals(resourceId)) {
          found = true;
          break;
        }
      }
      if (!found) {
        return false;
      }
    }

    // Check scope permissions
    return switch (delegation.getScope()) {
      case "full" -> true;
      case "read_only" -> permission.endsWith(":read") || permission.endsWith(":list");
      case "transactions_only" -> resourceType.equals("transaction");
      default -> false;
    };
  }
}
