package org.budgetanalyzer.permission.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.budgetanalyzer.permission.api.request.UserFilter;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.repository.spec.UserSpecifications;
import org.budgetanalyzer.permission.service.UserSyncService;

@DataJpaTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "spring.jpa.hibernate.ddl-auto=validate",
    })
@Import(UserSyncService.class)
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("User identity model after Flyway migrations")
class UserIdentityIntegrationTest {

  @Container
  private static final PostgreSQLContainer<?> postgreSQLContainer =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @Autowired private UserRepository userRepository;

  @Autowired private UserSyncService userSyncService;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
    dynamicPropertyRegistry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
    dynamicPropertyRegistry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    dynamicPropertyRegistry.add("spring.datasource.password", postgreSQLContainer::getPassword);
    dynamicPropertyRegistry.add(
        "spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Nested
  @DisplayName("email profile field")
  class EmailProfileFieldTests {

    @Test
    @DisplayName("allows duplicate active emails and still supports admin email search")
    void allowsDuplicateActiveEmailsAndStillSupportsAdminEmailSearch() {
      userRepository.saveAndFlush(
          createUser("usr_email0001", "auth0|email-1", "shared@example.com", "Email User One"));
      userRepository.saveAndFlush(
          createUser("usr_email0002", "auth0|email-2", "shared@example.com", "Email User Two"));

      var sharedEmailUsers =
          userRepository.findAllNotDeleted(
              UserSpecifications.withFilter(
                  new UserFilter(
                      null, "shared@example.com", null, null, null, null, null, null, null)));

      assertThat(sharedEmailUsers)
          .extracting(User::getIdpSub)
          .containsExactlyInAnyOrder("auth0|email-1", "auth0|email-2");
    }

    @Test
    @DisplayName(
        "updates an existing user by idpSub even when another active user already has "
            + "the target email")
    void updatesAnExistingUserByIdpSubEvenWhenAnotherActiveUserAlreadyHasTheTargetEmail() {
      userRepository.saveAndFlush(
          createUser("usr_sync0001", "auth0|other-user", "shared@example.com", "Other User"));
      userRepository.saveAndFlush(
          createUser("usr_sync0002", "auth0|target-user", "old@example.com", "Old Name"));

      var syncedUser =
          userSyncService.syncUser("auth0|target-user", "shared@example.com", "Updated Name");

      assertThat(syncedUser.getId()).isEqualTo("usr_sync0002");
      assertThat(syncedUser.getIdpSub()).isEqualTo("auth0|target-user");
      assertThat(syncedUser.getEmail()).isEqualTo("shared@example.com");
      assertThat(syncedUser.getDisplayName()).isEqualTo("Updated Name");
      assertThat(userRepository.findByIdpSubAndDeletedFalse("auth0|target-user"))
          .get()
          .extracting(User::getEmail, User::getDisplayName)
          .containsExactly("shared@example.com", "Updated Name");
    }
  }

  @Nested
  @DisplayName("idpSub constraint")
  class IdpSubConstraintTests {

    @Test
    @DisplayName("keeps idpSub unique among active users")
    void keepsIdpSubUniqueAmongActiveUsers() {
      userRepository.saveAndFlush(
          createUser("usr_idpsub01", "auth0|stable-sub", "first@example.com", "First User"));

      assertThatThrownBy(
              () ->
                  userRepository.saveAndFlush(
                      createUser(
                          "usr_idpsub02", "auth0|stable-sub", "second@example.com", "Second User")))
          .isInstanceOf(DataIntegrityViolationException.class);
    }
  }

  private User createUser(String id, String idpSub, String email, String displayName) {
    return new User(id, idpSub, email, displayName);
  }
}
