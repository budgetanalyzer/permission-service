package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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
import org.budgetanalyzer.permission.service.exception.UserDeactivatedException;

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
      when(userRepository.existsByIdpSubAndStatus(TestConstants.TEST_IDP_SUB, "DEACTIVATED"))
          .thenReturn(false);
      when(userRepository.findByIdpSubAndDeletedFalse(TestConstants.TEST_IDP_SUB))
          .thenReturn(Optional.empty());
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      var defaultRole = new Role();
      defaultRole.setId("USER");
      when(roleRepository.findByIdActive("USER")).thenReturn(Optional.of(defaultRole));

      // Act
      var result =
          userSyncService.syncUser(
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getIdpSub()).isEqualTo(TestConstants.TEST_IDP_SUB);
      assertThat(result.getEmail()).isEqualTo(TestConstants.TEST_EMAIL);
      assertThat(result.getDisplayName()).isEqualTo(TestConstants.TEST_DISPLAY_NAME);

      verify(userRoleRepository).save(any(UserRole.class));
    }

    @Test
    @DisplayName("should update existing user")
    void shouldUpdateExistingUser() {
      // Arrange
      when(userRepository.existsByIdpSubAndStatus(TestConstants.TEST_IDP_SUB, "DEACTIVATED"))
          .thenReturn(false);
      var existingUser = new User();
      existingUser.setId(TestConstants.TEST_USER_ID);
      existingUser.setIdpSub(TestConstants.TEST_IDP_SUB);
      existingUser.setEmail("old@example.com");
      existingUser.setDisplayName("Old Name");

      when(userRepository.findByIdpSubAndDeletedFalse(TestConstants.TEST_IDP_SUB))
          .thenReturn(Optional.of(existingUser));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      // Act
      var result =
          userSyncService.syncUser(
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);

      // Assert
      assertThat(result.getEmail()).isEqualTo(TestConstants.TEST_EMAIL);
      assertThat(result.getDisplayName()).isEqualTo(TestConstants.TEST_DISPLAY_NAME);

      verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("should not assign default role when role not configured")
    void shouldNotAssignDefaultRoleWhenNotConfigured() {
      // Arrange
      when(userRepository.existsByIdpSubAndStatus(TestConstants.TEST_IDP_SUB, "DEACTIVATED"))
          .thenReturn(false);
      when(userRepository.findByIdpSubAndDeletedFalse(TestConstants.TEST_IDP_SUB))
          .thenReturn(Optional.empty());
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
      when(roleRepository.findByIdActive("USER")).thenReturn(Optional.empty());

      // Act
      var result =
          userSyncService.syncUser(
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);

      // Assert
      assertThat(result).isNotNull();
      verify(userRoleRepository, never()).save(any());
    }

    @Test
    @DisplayName("should assign default role with correct userId and roleId")
    void shouldAssignDefaultRoleCorrectly() {
      // Arrange
      when(userRepository.existsByIdpSubAndStatus(TestConstants.TEST_IDP_SUB, "DEACTIVATED"))
          .thenReturn(false);
      when(userRepository.findByIdpSubAndDeletedFalse(TestConstants.TEST_IDP_SUB))
          .thenReturn(Optional.empty());
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      var defaultRole = new Role();
      defaultRole.setId("USER");
      when(roleRepository.findByIdActive("USER")).thenReturn(Optional.of(defaultRole));

      // Act
      userSyncService.syncUser(
          TestConstants.TEST_IDP_SUB, TestConstants.TEST_EMAIL, TestConstants.TEST_DISPLAY_NAME);

      // Assert
      var captor = ArgumentCaptor.forClass(UserRole.class);
      verify(userRoleRepository).save(captor.capture());
      assertThat(captor.getValue().getRoleId()).isEqualTo("USER");
    }
  }

  @Nested
  @DisplayName("syncUser deactivation gate")
  class SyncUserDeactivationGateTests {

    @Test
    @DisplayName("should throw when user is deactivated")
    void shouldThrowWhenUserIsDeactivated() {
      // Arrange
      when(userRepository.existsByIdpSubAndStatus(TestConstants.TEST_IDP_SUB, "DEACTIVATED"))
          .thenReturn(true);

      // Act & Assert
      assertThatThrownBy(
              () ->
                  userSyncService.syncUser(
                      TestConstants.TEST_IDP_SUB,
                      TestConstants.TEST_EMAIL,
                      TestConstants.TEST_DISPLAY_NAME))
          .isInstanceOf(UserDeactivatedException.class);

      verify(userRepository, never()).findByIdpSubAndDeletedFalse(any());
      verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("should create user when not deactivated")
    void shouldCreateUserWhenNotDeactivated() {
      // Arrange
      when(userRepository.existsByIdpSubAndStatus(TestConstants.TEST_IDP_SUB, "DEACTIVATED"))
          .thenReturn(false);
      when(userRepository.findByIdpSubAndDeletedFalse(TestConstants.TEST_IDP_SUB))
          .thenReturn(Optional.empty());
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));
      when(roleRepository.findByIdActive("USER")).thenReturn(Optional.empty());

      // Act
      var result =
          userSyncService.syncUser(
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getIdpSub()).isEqualTo(TestConstants.TEST_IDP_SUB);
      verify(userRepository).save(any(User.class));
    }

    @Test
    @DisplayName("should update active user when not deactivated")
    void shouldUpdateActiveUserWhenNotDeactivated() {
      // Arrange
      when(userRepository.existsByIdpSubAndStatus(TestConstants.TEST_IDP_SUB, "DEACTIVATED"))
          .thenReturn(false);

      var existingUser = new User();
      existingUser.setId(TestConstants.TEST_USER_ID);
      existingUser.setIdpSub(TestConstants.TEST_IDP_SUB);
      existingUser.setEmail("old@example.com");
      existingUser.setDisplayName("Old Name");

      when(userRepository.findByIdpSubAndDeletedFalse(TestConstants.TEST_IDP_SUB))
          .thenReturn(Optional.of(existingUser));
      when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

      // Act
      var result =
          userSyncService.syncUser(
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME);

      // Assert
      assertThat(result.getEmail()).isEqualTo(TestConstants.TEST_EMAIL);
      assertThat(result.getDisplayName()).isEqualTo(TestConstants.TEST_DISPLAY_NAME);
      verify(userRoleRepository, never()).save(any());
    }
  }
}
