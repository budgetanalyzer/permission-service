package org.budgetanalyzer.permission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.domain.Permission;
import org.budgetanalyzer.permission.domain.Role;
import org.budgetanalyzer.permission.domain.RolePermission;
import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.domain.UserRole;

@DataJpaTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "spring.jpa.hibernate.ddl-auto=validate",
    })
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("UserRoleRepository")
class UserRoleRepositoryIntegrationTest {

  private static final String TEST_ROLE_ID = "ROLE_REPOSITORY_TEST";
  private static final String TEST_PERMISSION_READ_ID = "repositorytest:read";
  private static final String TEST_PERMISSION_WRITE_ID = "repositorytest:write";

  @Container
  private static final PostgreSQLContainer<?> postgreSQLContainer =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @Autowired private UserRoleRepository userRoleRepository;

  @Autowired private TestEntityManager testEntityManager;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
    dynamicPropertyRegistry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
    dynamicPropertyRegistry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    dynamicPropertyRegistry.add("spring.datasource.password", postgreSQLContainer::getPassword);
    dynamicPropertyRegistry.add(
        "spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
  }

  @Nested
  @DisplayName("findPermissionIdsByUserId")
  class FindPermissionIdsByUserIdTests {

    @Test
    @DisplayName("should return permissions for the user's assigned roles")
    void shouldReturnPermissionsForTheUsersAssignedRoles() {
      testEntityManager.persist(
          new User(
              TestConstants.TEST_USER_ID,
              TestConstants.TEST_IDP_SUB,
              TestConstants.TEST_EMAIL,
              TestConstants.TEST_DISPLAY_NAME));

      testEntityManager.persist(
          new Role(
              TEST_ROLE_ID, "Repository Test Role", "Role used by repository integration test"));

      testEntityManager.persist(
          new Permission(
              TEST_PERMISSION_READ_ID,
              "Repository Test Read",
              "Read permission for repository integration test",
              "repositorytest",
              "read"));
      testEntityManager.persist(
          new Permission(
              TEST_PERMISSION_WRITE_ID,
              "Repository Test Write",
              "Write permission for repository integration test",
              "repositorytest",
              "write"));

      var userRole = new UserRole();
      userRole.setUserId(TestConstants.TEST_USER_ID);
      userRole.setRoleId(TEST_ROLE_ID);
      testEntityManager.persist(userRole);

      var rolePermissionRead = new RolePermission();
      rolePermissionRead.setRoleId(TEST_ROLE_ID);
      rolePermissionRead.setPermissionId(TEST_PERMISSION_READ_ID);
      testEntityManager.persist(rolePermissionRead);

      var rolePermissionWrite = new RolePermission();
      rolePermissionWrite.setRoleId(TEST_ROLE_ID);
      rolePermissionWrite.setPermissionId(TEST_PERMISSION_WRITE_ID);
      testEntityManager.persist(rolePermissionWrite);

      testEntityManager.flush();

      var permissionIds = userRoleRepository.findPermissionIdsByUserId(TestConstants.TEST_USER_ID);

      assertThat(permissionIds)
          .containsExactlyInAnyOrder(TEST_PERMISSION_READ_ID, TEST_PERMISSION_WRITE_ID);
    }
  }
}
