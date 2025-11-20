package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.domain.Role;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.domain.UserRole;
import org.budgetanalyzer.permission.repository.RoleRepository;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserSyncService")
class UserSyncServiceTest {

  @Mock private UserRepository userRepository;
  @Mock private UserRoleRepository userRoleRepository;
  @Mock private RoleRepository roleRepository;

  private UserSyncService userSyncService;

  @BeforeEach
  void setUp() {
    userSyncService = new UserSyncService(userRepository, userRoleRepository, roleRepository);
  }

  @Nested
  @DisplayName("syncUser")
  class SyncUserTests {

    @Test
    @DisplayName("should create new user when not found")
    void shouldCreateNewUserWhenNotFound() {
      // Arrange
      when(userRepository.findByAuth0Sub(TestConstants.TEST_AUTH0_SUB))
          .thenReturn(Optional.empty());
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      var defaultRole = new Role();
      defaultRole.setId("USER");
      when(roleRepository.findByIdAndDeletedFalse("USER")).thenReturn(Optional.of(defaultRole));

      // Act
      var result =
          userSyncService.syncUser(
              TestConstants.TEST_AUTH0_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getAuth0Sub()).isEqualTo(TestConstants.TEST_AUTH0_SUB);
      assertThat(result.getEmail()).isEqualTo(TestConstants.TEST_EMAIL);
      assertThat(result.getDisplayName()).isEqualTo(TestConstants.TEST_DISPLAY_NAME);

      // Verify default role assignment
      verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    @DisplayName("should update existing user")
    void shouldUpdateExistingUser() {
      // Arrange
      var existingUser = new User();
      existingUser.setId(TestConstants.TEST_USER_ID);
      existingUser.setAuth0Sub(TestConstants.TEST_AUTH0_SUB);
      existingUser.setEmail("old@example.com");
      existingUser.setDisplayName("Old Name");

      when(userRepository.findByAuth0Sub(TestConstants.TEST_AUTH0_SUB))
          .thenReturn(Optional.of(existingUser));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      // Act
      var result =
          userSyncService.syncUser(
              TestConstants.TEST_AUTH0_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);

      // Assert
      assertThat(result.getEmail()).isEqualTo(TestConstants.TEST_EMAIL);
      assertThat(result.getDisplayName()).isEqualTo(TestConstants.TEST_DISPLAY_NAME);

      // Verify no new role assignment for existing user
      verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("should restore soft-deleted user on sync")
    void shouldRestoreSoftDeletedUser() {
      // Arrange
      var deletedUser = new User();
      deletedUser.setId(TestConstants.TEST_USER_ID);
      deletedUser.setAuth0Sub(TestConstants.TEST_AUTH0_SUB);
      deletedUser.markDeleted(TestConstants.TEST_ADMIN_ID);

      when(userRepository.findByAuth0Sub(TestConstants.TEST_AUTH0_SUB))
          .thenReturn(Optional.of(deletedUser));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      // Act
      var result =
          userSyncService.syncUser(
              TestConstants.TEST_AUTH0_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);

      // Assert
      assertThat(result.isDeleted()).isFalse();
    }

    @Test
    @DisplayName("should not assign default role when role not configured")
    void shouldNotAssignDefaultRoleWhenNotConfigured() {
      // Arrange
      when(userRepository.findByAuth0Sub(TestConstants.TEST_AUTH0_SUB))
          .thenReturn(Optional.empty());
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
      when(roleRepository.findByIdAndDeletedFalse("USER")).thenReturn(Optional.empty());

      // Act
      var result =
          userSyncService.syncUser(
              TestConstants.TEST_AUTH0_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);

      // Assert
      assertThat(result).isNotNull();
      verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("should assign default role with SYSTEM as granter")
    void shouldAssignDefaultRoleWithSystemAsGranter() {
      // Arrange
      when(userRepository.findByAuth0Sub(TestConstants.TEST_AUTH0_SUB))
          .thenReturn(Optional.empty());
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      var defaultRole = new Role();
      defaultRole.setId("USER");
      when(roleRepository.findByIdAndDeletedFalse("USER")).thenReturn(Optional.of(defaultRole));

      // Act
      userSyncService.syncUser(
          TestConstants.TEST_AUTH0_SUB, TestConstants.TEST_EMAIL, TestConstants.TEST_DISPLAY_NAME);

      // Assert
      var captor = ArgumentCaptor.forClass(UserRole.class);
      verify(userRoleRepository).save(captor.capture());
      assertThat(captor.getValue().getRoleId()).isEqualTo("USER");
      assertThat(captor.getValue().getGrantedBy()).isEqualTo("SYSTEM");
    }
  }
}
