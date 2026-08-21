package org.budgetanalyzer.permission.service;

import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;

import java.util.Arrays;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;

import com.github.tomakehurst.wiremock.WireMockServer;

import org.budgetanalyzer.permission.domain.User;
import org.budgetanalyzer.permission.domain.UserRole;
import org.budgetanalyzer.permission.repository.UserRepository;
import org.budgetanalyzer.permission.repository.UserRoleRepository;

/** Shared PostgreSQL and WireMock support for full-context permission-service tests. */
@SpringBootTest(
    properties = {
      "spring.flyway.enabled=true",
      "spring.flyway.clean-disabled=false",
      "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect",
      "spring.jpa.hibernate.ddl-auto=validate",
      "session-gateway.revocation.max-attempts=3",
      "session-gateway.revocation.initial-delay=0ms",
      "session-gateway.revocation.multiplier=1.0",
      "session-gateway.revocation.max-delay=0ms",
    })
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public abstract class PermissionServiceIntegrationTestSupport {

  protected static final WireMockServer wireMockServer = startWireMockServer();

  private static final PostgreSQLContainer<?> postgreSQLContainer = startPostgreSQLContainer();

  @Autowired protected UserRepository userRepository;

  @Autowired protected UserRoleRepository userRoleRepository;

  @Autowired private Flyway flyway;

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry dynamicPropertyRegistry) {
    dynamicPropertyRegistry.add("spring.datasource.url", postgreSQLContainer::getJdbcUrl);
    dynamicPropertyRegistry.add("spring.datasource.username", postgreSQLContainer::getUsername);
    dynamicPropertyRegistry.add("spring.datasource.password", postgreSQLContainer::getPassword);
    dynamicPropertyRegistry.add(
        "spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    dynamicPropertyRegistry.add(
        "session-gateway.base-url", () -> wireMockServer.baseUrl() + "/session-gateway");
  }

  /** Restores the migrated database baseline and resets Session Gateway HTTP stubs. */
  @BeforeEach
  protected void resetPersistenceAndExternalBoundary() {
    wireMockServer.resetAll();
    flyway.clean();
    flyway.migrate();
  }

  /**
   * Persists a user with the supplied identity and profile fields.
   *
   * @param id the internal user ID
   * @param idpSub the identity-provider subject
   * @param email the email address
   * @param displayName the display name
   * @return the persisted user
   */
  protected User persistUser(String id, String idpSub, String email, String displayName) {
    return userRepository.saveAndFlush(new User(id, idpSub, email, displayName));
  }

  /**
   * Assigns seeded roles to a persisted user.
   *
   * @param userId the internal user ID
   * @param roleIds the seeded role IDs
   */
  protected void assignRoles(String userId, String... roleIds) {
    Arrays.stream(roleIds)
        .map(roleId -> createUserRole(userId, roleId))
        .forEach(userRoleRepository::save);
    userRoleRepository.flush();
  }

  private static WireMockServer startWireMockServer() {
    var server = new WireMockServer(wireMockConfig().dynamicPort());
    server.start();
    return server;
  }

  private static PostgreSQLContainer<?> startPostgreSQLContainer() {
    var container =
        new PostgreSQLContainer<>("postgres:17-alpine")
            .withDatabaseName("testdb")
            .withUsername("test")
            .withPassword("test");
    container.start();
    return container;
  }

  private UserRole createUserRole(String userId, String roleId) {
    var userRole = new UserRole();
    userRole.setUserId(userId);
    userRole.setRoleId(roleId);
    return userRole;
  }
}
