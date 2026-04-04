package org.budgetanalyzer.permission.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

/** Central configuration for permission-service beans. */
@Configuration
public class PermissionServiceConfig {

  /**
   * Creates a RestClient pre-configured with the Session Gateway base URL.
   *
   * @param baseUrl the Session Gateway base URL
   * @return a configured RestClient instance
   */
  @Bean
  public RestClient sessionGatewayRestClient(@Value("${session-gateway.base-url}") String baseUrl) {
    return RestClient.builder().baseUrl(baseUrl).build();
  }
}
