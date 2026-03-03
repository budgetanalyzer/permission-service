package org.budgetanalyzer.permission.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.domain.UserRole;
import org.budgetanalyzer.permission.repository.RoleRepository;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;

/**
 * Service for synchronizing users with an identity provider.
 *
 * <p>Handles user creation and updates based on identity provider authentication data. This service
 * is provider-agnostic — it accepts any OIDC {@code sub} claim. The current deployment uses Auth0,
 * but no Auth0-specific logic exists here.
 */
@Service
@Transactional
public class UserSyncService {

  private static final String DEFAULT_ROLE = "USER";

  private final UserRepository userRepository;
  private final UserRoleRepository userRoleRepository;
  private final RoleRepository roleRepository;

  /**
   * Constructs a UserSyncService with the required repositories.
   *
   * @param userRepository the user repository
   * @param userRoleRepository the user-role join table repository
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
   * Syncs a user from identity provider data, creating or updating as needed.
   *
   * <p>On first login the user is created and assigned the default USER role. On subsequent logins,
   * email and display name are updated.
   *
   * @param idpSub the identity provider subject identifier
   * @param email the user's email address
   * @param displayName the user's display name
   * @return the synced user
   */
  public User syncUser(String idpSub, String email, String displayName) {
    return userRepository
        .findByIdpSubAndDeletedFalse(idpSub)
        .map(user -> updateUser(user, email, displayName))
        .orElseGet(() -> createUser(idpSub, email, displayName));
  }

  private User createUser(String idpSub, String email, String displayName) {
    var user = new User();
    user.setId(generateUserId());
    user.setIdpSub(idpSub);
    user.setEmail(email);
    user.setDisplayName(displayName);

    var savedUser = userRepository.save(user);

    assignDefaultRole(savedUser);

    return savedUser;
  }

  private User updateUser(User user, String email, String displayName) {
    user.setEmail(email);
    user.setDisplayName(displayName);
    return userRepository.save(user);
  }

  private void assignDefaultRole(User user) {
    var defaultRole = roleRepository.findByIdAndDeletedFalse(DEFAULT_ROLE);
    if (defaultRole.isEmpty()) {
      return;
    }

    var userRole = new UserRole();
    userRole.setUserId(user.getId());
    userRole.setRoleId(DEFAULT_ROLE);
    userRoleRepository.save(userRole);
  }

  private String generateUserId() {
    return "usr_" + UUID.randomUUID().toString().replace("-", "");
  }
}
