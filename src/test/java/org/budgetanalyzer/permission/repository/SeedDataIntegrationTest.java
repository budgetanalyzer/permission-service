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
 * Verifies the shape of the Flyway-seeded data after V5. Asserts the expected permission count, the
 * new {@code transactions:*:any} rows, and the ADMIN/USER role-permission mappings.
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

  private static final int EXPECTED_TOTAL_PERMISSIONS = 24;
  private static final int EXPECTED_ADMIN_PERMISSIONS = 24;
  // V2 grants USER 6 permissions; V3 adds statementformats:read and currencies:read; V5
  // intentionally does not change USER, so the post-V5 count is 8.
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

      assertThat(permissions).hasSize(EXPECTED_TOTAL_PERMISSIONS);
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
    @DisplayName("ADMIN role bundles all 24 permissions")
    void adminRoleBundlesAllPermissions() {
      Long adminCount = countPermissionsForRole(ADMIN_ROLE_ID);

      assertThat(adminCount).isEqualTo((long) EXPECTED_ADMIN_PERMISSIONS);
    }

    @Test
    @DisplayName("ADMIN role grants the three new cross-user transaction permissions")
    void adminRoleGrantsTheThreeNewCrossUserTransactionPermissions() {
      Set<String> adminPermissionIds = findPermissionIdsForRole(ADMIN_ROLE_ID);

      assertThat(adminPermissionIds)
          .contains(TRANSACTIONS_READ_ANY, TRANSACTIONS_WRITE_ANY, TRANSACTIONS_DELETE_ANY);
    }

    @Test
    @DisplayName("USER role count is unchanged by V5 and excludes the cross-user variants")
    void userRoleCountIsUnchangedByV5AndExcludesTheCrossUserVariants() {
      Long userCount = countPermissionsForRole(USER_ROLE_ID);
      Set<String> userPermissionIds = findPermissionIdsForRole(USER_ROLE_ID);

      assertThat(userCount).isEqualTo((long) EXPECTED_USER_PERMISSIONS);
      assertThat(userPermissionIds)
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
