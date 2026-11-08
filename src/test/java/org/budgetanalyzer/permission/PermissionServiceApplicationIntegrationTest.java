package org.budgetanalyzer.permission;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import org.budgetanalyzer.service.config.HttpLoggingProperties;

// src/test/resources/application.yml shadows the main application.yml, so load the shipped file.
@SpringBootTest(properties = "spring.config.additional-location=file:./src/main/resources/")
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("kubernetes")
@TestMethodOrder(OrderAnnotation.class)
class PermissionServiceApplicationIntegrationTest {

  @Container
  private static final PostgreSQLContainer POSTGRESQL_CONTAINER =
      new PostgreSQLContainer("postgres:17-alpine")
          .withDatabaseName("testdb")
          .withUsername("test")
          .withPassword("test");

  @Autowired private Environment environment;
  @Autowired private HttpLoggingProperties httpLoggingProperties;
  @Autowired private MockMvc mockMvc;

  @DynamicPropertySource
  static void configureDatasource(DynamicPropertyRegistry dynamicPropertyRegistry) {
    dynamicPropertyRegistry.add("spring.datasource.url", POSTGRESQL_CONTAINER::getJdbcUrl);
    dynamicPropertyRegistry.add("spring.datasource.username", POSTGRESQL_CONTAINER::getUsername);
    dynamicPropertyRegistry.add("spring.datasource.password", POSTGRESQL_CONTAINER::getPassword);
    dynamicPropertyRegistry.add(
        "spring.datasource.driver-class-name", () -> "org.postgresql.Driver");
    dynamicPropertyRegistry.add("spring.datasource.hikari.connection-timeout", () -> "1000");
  }

  @Test
  @Order(1)
  void kubernetesProfileUsesSafeLoggingDefaults() {
    assertThat(environment.getProperty("logging.level.org.budgetanalyzer")).isEqualTo("INFO");
    assertThat(environment.getProperty("logging.level.budgetanalyzer.audit")).isEqualTo("INFO");
    assertThat(httpLoggingProperties.isIncludeRequestBody()).isFalse();
    assertThat(httpLoggingProperties.isIncludeResponseBody()).isFalse();
  }

  @Test
  @Order(2)
  void readinessFailsButLivenessRemainsUpWhenDatabaseStops() throws Exception {
    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());

    POSTGRESQL_CONTAINER.stop();

    mockMvc.perform(get("/actuator/health/readiness")).andExpect(status().isServiceUnavailable());
    mockMvc.perform(get("/actuator/health/liveness")).andExpect(status().isOk());
  }
}
