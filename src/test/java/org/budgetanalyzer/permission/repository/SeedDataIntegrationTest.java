package org.budgetanalyzer.permission.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

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

import org.budgetanalyzer.permission.domain.Permission;

/**
 * Verifies the consolidated Flyway seed data. Asserts the expected permission count, the
 * transaction {@code :any} rows, and the ADMIN/USER role-permission mappings.
 */
@DataJpaTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "spring.jpa.hibernate.ddl-auto=validate",
    })
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@DisplayName("Seed data after Flyway migrations")
class SeedDataIntegrationTest {

  private static final String ADMIN_ROLE_ID = "ADMIN";
  private static final String USER_ROLE_ID = "USER";

  private static final String TRANSACTIONS_READ_ANY = "transactions:read:any";
  private static final String TRANSACTIONS_WRITE_ANY = "transactions:write:any";
  private static final String TRANSACTIONS_DELETE_ANY = "transactions:delete:any";
  private static final String TRANSACTIONS_DELETE = "transactions:delete";
  private static final String STATEMENT_FORMATS_DELETE = "statementformats:delete";
  private static final String VIEWS_READ = "views:read";
  private static final String VIEWS_WRITE = "views:write";
  private static final String VIEWS_DELETE = "views:delete";

  private static final int EXPECTED_TOTAL_PERMISSIONS = 16;
  private static final int EXPECTED_ADMIN_PERMISSIONS = 13;
  // USER gets transactions read/write/delete, views read/write/delete, statementformats:read, and
  // currencies:read.
  private static final int EXPECTED_USER_PERMISSIONS = 8;

  @Container
  private static final PostgreSQLContainer<?> postgreSQLContainer =
      new PostgreSQLContainer<>("postgres:17-alpine")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @Autowired private PermissionRepository permissionRepository;

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
  @DisplayName("permissions table")
  class PermissionsTableTests {

    @Test
    @DisplayName("contains exactly the expected number of seeded permissions")
    void containsExactlyTheExpectedNumberOfSeededPermissions() {
      List<Permission> permissions = permissionRepository.findAllByDeletedFalse();

      assertThat(permissions)
          .hasSize(EXPECTED_TOTAL_PERMISSIONS)
          .extracting(Permission::getId)
          .doesNotContain(STATEMENT_FORMATS_DELETE);
    }

    @Test
    @DisplayName("contains the three new cross-user transaction permissions")
    void containsTheThreeNewCrossUserTransactionPermissions() {
      List<Permission> permissions = permissionRepository.findAllByDeletedFalse();

      assertThat(permissions)
          .extracting(Permission::getId)
          .contains(TRANSACTIONS_READ_ANY, TRANSACTIONS_WRITE_ANY, TRANSACTIONS_DELETE_ANY);
    }
  }

  @Nested
  @DisplayName("role_permissions table")
  class RolePermissionsTableTests {

    @Test
    @DisplayName(
        "ADMIN role bundles the 13 non-view permissions while excluding saved views and "
            + "statement format deletion")
    void adminRoleBundlesThirteenNonViewPermissions() {
      Long adminCount = countPermissionsForRole(ADMIN_ROLE_ID);
      Set<String> adminPermissionIds = findPermissionIdsForRole(ADMIN_ROLE_ID);

      assertThat(adminCount).isEqualTo((long) EXPECTED_ADMIN_PERMISSIONS);
      assertThat(adminPermissionIds)
          .doesNotContain(VIEWS_READ, VIEWS_WRITE, VIEWS_DELETE, STATEMENT_FORMATS_DELETE);
    }

    @Test
    @DisplayName("ADMIN role grants the three new cross-user transaction permissions")
    void adminRoleGrantsTheThreeNewCrossUserTransactionPermissions() {
      Set<String> adminPermissionIds = findPermissionIdsForRole(ADMIN_ROLE_ID);

      assertThat(adminPermissionIds)
          .contains(TRANSACTIONS_READ_ANY, TRANSACTIONS_WRITE_ANY, TRANSACTIONS_DELETE_ANY);
    }

    @Test
    @DisplayName(
        "USER role has eight permissions including transactions delete and excludes the "
            + "cross-user variants")
    void userRoleHasEightPermissionsIncludingTransactionsDeleteAndExcludesTheCrossUserVariants() {
      Long userCount = countPermissionsForRole(USER_ROLE_ID);
      Set<String> userPermissionIds = findPermissionIdsForRole(USER_ROLE_ID);

      assertThat(userCount).isEqualTo((long) EXPECTED_USER_PERMISSIONS);
      assertThat(userPermissionIds)
          .contains(TRANSACTIONS_DELETE, VIEWS_READ, VIEWS_WRITE, VIEWS_DELETE)
          .doesNotContain(TRANSACTIONS_READ_ANY, TRANSACTIONS_WRITE_ANY, TRANSACTIONS_DELETE_ANY);
    }
  }

  private Long countPermissionsForRole(String roleId) {
    return testEntityManager
        .getEntityManager()
        .createQuery(
            "SELECT COUNT(rp) FROM RolePermission rp WHERE rp.roleId = :roleId", Long.class)
        .setParameter("roleId", roleId)
        .getSingleResult();
  }

  private Set<String> findPermissionIdsForRole(String roleId) {
    List<String> ids =
        testEntityManager
            .getEntityManager()
            .createQuery(
                "SELECT rp.permissionId FROM RolePermission rp WHERE rp.roleId = :roleId",
                String.class)
            .setParameter("roleId", roleId)
            .getResultList();
    return Set.copyOf(ids);
  }
}
