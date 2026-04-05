package org.budgetanalyzer.permission.service;

import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import org.budgetanalyzer.permission.client.SessionGatewayClient;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;
import org.budgetanalyzer.permission.service.dto.UserDeactivationResult;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;
import org.budgetanalyzer.service.exception.ServiceUnavailableException;

/**
 * Service for user management with soft delete support.
 *
 * <p>Handles user queries and soft deletion.
 */
@Service
@Transactional(readOnly = true)
public class UserService {

  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;
  private final SessionGatewayClient sessionGatewayClient;
  private final TransactionTemplate transactionTemplate;

  /**
   * Constructs a UserService with the required dependencies.
   *
   * @param userRepository the user repository
   * @param userRoleRepository the user-role join table repository
   * @param sessionGatewayClient the Session Gateway client for session revocation
   * @param transactionTemplate the transaction template for durable deactivation state changes
   */
  public UserService(
      UserRepository userRepository,
      UserRoleRepository userRoleRepository,
      SessionGatewayClient sessionGatewayClient,
      TransactionTemplate transactionTemplate) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
    this.sessionGatewayClient = sessionGatewayClient;
    this.transactionTemplate = transactionTemplate;
  }

  /**
   * Returns a non-deleted user by ID.
   *
   * @param id the user ID
   * @return the user
   * @throws ResourceNotFoundException if the user does not exist
   */
  public User getUser(String id) {
    return userRepository
        .findByIdNotDeleted(id)
        .orElseThrow(() -> new ResourceNotFoundException("User not found: " + id));
  }

  /**
   * Soft-deletes a user and removes all role assignments.
   *
   * @param id the user ID
   * @param deletedBy the user ID performing the deletion
   */
  @Transactional
  public void deleteUser(String id, String deletedBy) {
    var user = getUser(id);

    userRoleRepository.deleteByUserId(id);

    user.markDeleted(deletedBy);
    userRepository.save(user);
  }

  /**
   * Deactivates a user, removes all role assignments, and revokes active sessions.
   *
   * <p>Idempotent — deactivating an already-deactivated user skips the state change but still
   * attempts session revocation. Soft-deleted users are treated as not found. The state change is
   * committed before session revocation is attempted so PostgreSQL remains the durable gate.
   *
   * @param userId the user ID to deactivate
   * @param deactivatedBy the user ID performing the deactivation
   * @return the deactivation result including partial failure information
   * @throws ResourceNotFoundException if the user does not exist or is soft-deleted
   */
  @Transactional(propagation = Propagation.NOT_SUPPORTED)
  public UserDeactivationResult deactivateUser(String userId, String deactivatedBy) {
    var persistedUserDeactivation =
        Objects.requireNonNull(
            transactionTemplate.execute(
                transactionStatus -> persistUserDeactivation(userId, deactivatedBy)),
            "User deactivation transaction returned null");

    var revocationResult = sessionGatewayClient.revokeUserSessions(userId);

    if (!revocationResult.revoked()) {
      throw new ServiceUnavailableException(
          "User " + userId + " was deactivated but session revocation failed; retry is safe");
    }

    return new UserDeactivationResult(
        persistedUserDeactivation.userId(),
        persistedUserDeactivation.status(),
        persistedUserDeactivation.rolesRemoved(),
        true);
  }

  private PersistedUserDeactivation persistUserDeactivation(String userId, String deactivatedBy) {
    var user =
        userRepository
            .findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

    if (user.isDeleted()) {
      throw new ResourceNotFoundException("User not found: " + userId);
    }

    var rolesRemoved = 0;
    if (!user.isDeactivated()) {
      rolesRemoved = userRoleRepository.deleteByUserId(userId);
      user.deactivate(deactivatedBy);
      userRepository.save(user);
    }

    return new PersistedUserDeactivation(user.getId(), user.getStatus(), rolesRemoved);
  }

  private record PersistedUserDeactivation(String userId, String status, int rolesRemoved) {}
}
