package org.budgetanalyzer.permission.service;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.domain.UserRole;
import org.budgetanalyzer.permission.repository.RoleRepository;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;

/**
 * Service for synchronizing users with Auth0.
 *
 * <p>Handles user creation and updates based on Auth0 authentication data.
 */
@Service
@Transactional
public class UserSyncService {

  private static final String DEFAULT_ROLE = "USER";

  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;
  private final RoleRepository roleRepository;

  /**
   * Constructs a new UserSyncService.
   *
   * @param userRepository the user repository
   * @param userRoleRepository the user role repository
   * @param roleRepository the role repository
   */
  public UserSyncService(
      UserRepository userRepository,
      UserRoleRepository userRoleRepository,
      RoleRepository roleRepository) {
    this.userRepository = userRepository;
    this.userRoleRepository = userRoleRepository;
    this.roleRepository = roleRepository;
  }

  /**
   * Synchronizes a user from Auth0 data.
   *
   * <p>Creates a new user if not found, or updates existing user data.
   *
   * @param auth0Sub the Auth0 subject identifier
   * @param email the user's email
   * @param displayName the user's display name
   * @return the synchronized user
   */
  public User syncUser(String auth0Sub, String email, String displayName) {
    return userRepository
        .findByAuth0Sub(auth0Sub)
        .map(user -> updateUser(user, email, displayName))
        .orElseGet(() -> createUser(auth0Sub, email, displayName));
  }

  private User createUser(String auth0Sub, String email, String displayName) {
    var user = new User();
    user.setId(generateUserId());
    user.setAuth0Sub(auth0Sub);
    user.setEmail(email);
    user.setDisplayName(displayName);

    var savedUser = userRepository.save(user);

    // Assign default USER role
    assignDefaultRole(savedUser);

    return savedUser;
  }

  private User updateUser(User user, String email, String displayName) {
    // Restore if previously deleted
    if (user.isDeleted()) {
      user.restore();
    }

    user.setEmail(email);
    user.setDisplayName(displayName);
    return userRepository.save(user);
  }

  private void assignDefaultRole(User user) {
    // Check if default role exists
    var defaultRole = roleRepository.findByIdAndDeletedFalse(DEFAULT_ROLE);
    if (defaultRole.isEmpty()) {
      return; // No default role configured
    }

    // Create user role assignment
    var userRole = new UserRole();
    userRole.setUserId(user.getId());
    userRole.setRoleId(DEFAULT_ROLE);
    userRole.setGrantedAt(Instant.now());
    userRole.setGrantedBy("SYSTEM");
    userRoleRepository.save(userRole);
  }

  private String generateUserId() {
    return "usr_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
  }
}
