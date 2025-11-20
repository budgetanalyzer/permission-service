package org.budgetanalyzer.permission.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.List;
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
import org.budgetanalyzer.permission.domain.Delegation;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.event.PermissionChangeEvent;
import org.budgetanalyzer.permission.repository.DelegationRepository;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.service.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
@DisplayName("DelegationService")
class DelegationServiceTest {

  @Mock private DelegationRepository delegationRepository;
  @Mock private UserRepository userRepository;
  @Mock private AuditService auditService;
  @Mock private PermissionCacheService permissionCacheService;

  private DelegationService delegationService;

  @BeforeEach
  void setUp() {
    delegationService =
        new DelegationService(
            delegationRepository, userRepository, auditService, permissionCacheService);
  }

  @Nested
  @DisplayName("createDelegation")
  class CreateDelegationTests {

    @Test
    @DisplayName("should create delegation successfully")
    void shouldCreateDelegationSuccessfully() {
      // Arrange
      var delegatee = new User();
      delegatee.setId(TestConstants.TEST_DELEGATEE_ID);
      when(userRepository.findByIdAndDeletedFalse(TestConstants.TEST_DELEGATEE_ID))
          .thenReturn(Optional.of(delegatee));

      var savedDelegation = new Delegation();
      savedDelegation.setId(1L);
      savedDelegation.setDelegatorId(TestConstants.TEST_USER_ID);
      savedDelegation.setDelegateeId(TestConstants.TEST_DELEGATEE_ID);
      savedDelegation.setScope("full");
      when(delegationRepository.save(any(Delegation.class))).thenReturn(savedDelegation);

      // Act
      var result =
          delegationService.createDelegation(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_DELEGATEE_ID,
              "full",
              null,
              null,
              null);

      // Assert
      assertThat(result).isNotNull();
      assertThat(result.getDelegatorId()).isEqualTo(TestConstants.TEST_USER_ID);

      verify(auditService).logPermissionChange(any(PermissionChangeEvent.class));
      verify(permissionCacheService).invalidateCache(TestConstants.TEST_DELEGATEE_ID);
    }

    @Test
    @DisplayName("should create delegation with expiration")
    void shouldCreateDelegationWithExpiration() {
      // Arrange
      var delegatee = new User();
      delegatee.setId(TestConstants.TEST_DELEGATEE_ID);
      when(userRepository.findByIdAndDeletedFalse(TestConstants.TEST_DELEGATEE_ID))
          .thenReturn(Optional.of(delegatee));

      var validUntil = Instant.now().plusSeconds(86400); // 24 hours
      when(delegationRepository.save(any(Delegation.class))).thenAnswer(inv -> inv.getArgument(0));

      // Act
      var result =
          delegationService.createDelegation(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_DELEGATEE_ID,
              "read_only",
              "transaction",
              new String[] {"txn_123"},
              validUntil);

      // Assert
      var captor = ArgumentCaptor.forClass(Delegation.class);
      verify(delegationRepository).save(captor.capture());
      assertThat(captor.getValue().getValidUntil()).isEqualTo(validUntil);
      assertThat(captor.getValue().getScope()).isEqualTo("read_only");
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when delegatee not found")
    void shouldThrowWhenDelegateeNotFound() {
      // Arrange
      when(userRepository.findByIdAndDeletedFalse(TestConstants.TEST_DELEGATEE_ID))
          .thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(
              () ->
                  delegationService.createDelegation(
                      TestConstants.TEST_USER_ID,
                      TestConstants.TEST_DELEGATEE_ID,
                      "full",
                      null,
                      null,
                      null))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Delegatee not found");
    }
  }

  @Nested
  @DisplayName("revokeDelegation")
  class RevokeDelegationTests {

    @Test
    @DisplayName("should revoke delegation successfully")
    void shouldRevokeDelegationSuccessfully() {
      // Arrange
      var delegation = new Delegation();
      delegation.setId(1L);
      delegation.setDelegatorId(TestConstants.TEST_USER_ID);
      delegation.setDelegateeId(TestConstants.TEST_DELEGATEE_ID);
      when(delegationRepository.findById(1L)).thenReturn(Optional.of(delegation));

      // Act
      delegationService.revokeDelegation(1L, TestConstants.TEST_USER_ID);

      // Assert
      var captor = ArgumentCaptor.forClass(Delegation.class);
      verify(delegationRepository).save(captor.capture());
      assertThat(captor.getValue().getRevokedAt()).isNotNull();
      assertThat(captor.getValue().getRevokedBy()).isEqualTo(TestConstants.TEST_USER_ID);

      verify(auditService).logPermissionChange(any(PermissionChangeEvent.class));
      verify(permissionCacheService).invalidateCache(TestConstants.TEST_DELEGATEE_ID);
    }

    @Test
    @DisplayName("should throw ResourceNotFoundException when delegation not found")
    void shouldThrowWhenDelegationNotFound() {
      // Arrange
      when(delegationRepository.findById(999L)).thenReturn(Optional.empty());

      // Act & Assert
      assertThatThrownBy(() -> delegationService.revokeDelegation(999L, TestConstants.TEST_USER_ID))
          .isInstanceOf(ResourceNotFoundException.class)
          .hasMessageContaining("Delegation not found");
    }
  }

  @Nested
  @DisplayName("getDelegationsForUser")
  class GetDelegationsForUserTests {

    @Test
    @DisplayName("should return both given and received delegations")
    void shouldReturnBothGivenAndReceivedDelegations() {
      // Arrange
      var givenDelegation = new Delegation();
      givenDelegation.setDelegatorId(TestConstants.TEST_USER_ID);
      givenDelegation.setDelegateeId(TestConstants.TEST_DELEGATEE_ID);

      var receivedDelegation = new Delegation();
      receivedDelegation.setDelegatorId(TestConstants.TEST_ADMIN_ID);
      receivedDelegation.setDelegateeId(TestConstants.TEST_USER_ID);

      when(delegationRepository.findByDelegatorIdAndRevokedAtIsNull(TestConstants.TEST_USER_ID))
          .thenReturn(List.of(givenDelegation));
      when(delegationRepository.findActiveDelegationsForUser(eq(TestConstants.TEST_USER_ID), any()))
          .thenReturn(List.of(receivedDelegation));

      // Act
      var result = delegationService.getDelegationsForUser(TestConstants.TEST_USER_ID);

      // Assert
      assertThat(result.given()).hasSize(1);
      assertThat(result.received()).hasSize(1);
    }
  }

  @Nested
  @DisplayName("hasDelegatedAccess")
  class HasDelegatedAccessTests {

    @Test
    @DisplayName("should return true for full scope delegation")
    void shouldReturnTrueForFullScopeDelegation() {
      // Arrange
      var delegation = new Delegation();
      delegation.setScope("full");
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_DELEGATEE_ID), any()))
          .thenReturn(List.of(delegation));

      // Act
      var result =
          delegationService.hasDelegatedAccess(
              TestConstants.TEST_DELEGATEE_ID, "transaction", "txn_123", "transactions:write");

      // Assert
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should return true for read_only scope with read permission")
    void shouldReturnTrueForReadOnlyScopeWithReadPermission() {
      // Arrange
      var delegation = new Delegation();
      delegation.setScope("read_only");
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_DELEGATEE_ID), any()))
          .thenReturn(List.of(delegation));

      // Act
      var result =
          delegationService.hasDelegatedAccess(
              TestConstants.TEST_DELEGATEE_ID, "transaction", "txn_123", "transactions:read");

      // Assert
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("should return false for read_only scope with write permission")
    void shouldReturnFalseForReadOnlyScopeWithWritePermission() {
      // Arrange
      var delegation = new Delegation();
      delegation.setScope("read_only");
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_DELEGATEE_ID), any()))
          .thenReturn(List.of(delegation));

      // Act
      var result =
          delegationService.hasDelegatedAccess(
              TestConstants.TEST_DELEGATEE_ID, "transaction", "txn_123", "transactions:write");

      // Assert
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("should return false when no delegations exist")
    void shouldReturnFalseWhenNoDelegations() {
      // Arrange
      when(delegationRepository.findActiveDelegationsForUser(
              eq(TestConstants.TEST_DELEGATEE_ID), any()))
          .thenReturn(List.of());

      // Act
      var result =
          delegationService.hasDelegatedAccess(
              TestConstants.TEST_DELEGATEE_ID, "transaction", "txn_123", "transactions:read");

      // Assert
      assertThat(result).isFalse();
    }
  }
}
