package org.budgetanalyzer.permission.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;

/**
 * Service-owned security overrides for permission-service.
 *
 * <p>The internal permissions endpoint ({@code /internal/v1/users/{idpSub}/permissions}) is called
 * by session-gateway before claims headers exist for the user request. This exception remains
 * path-scoped here instead of inside service-common so the shared library no longer grants
 * anonymous access to every {@code /internal/**} route. Runtime caller restriction is enforced by
 * orchestration through mesh identity and authorization policy.
 */
@Configuration
public class PermissionServiceSecurityConfig {

  private static final String INTERNAL_PERMISSIONS_ENDPOINT = "/internal/v1/users/*/permissions";

  /**
   * Permits the internal permissions endpoint without claims headers.
   *
   * @param http the servlet security builder
   * @return the configured filter chain
   * @throws Exception if the chain cannot be built
   */
  @Bean
  @Order(0)
  public SecurityFilterChain internalPermissionsSecurityFilterChain(HttpSecurity http)
      throws Exception {
    http.securityMatcher(INTERNAL_PERMISSIONS_ENDPOINT)
        .csrf(AbstractHttpConfigurer::disable)
        .sessionManagement(
            session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
        .securityContext(
            securityContext ->
                securityContext.securityContextRepository(new NullSecurityContextRepository()))
        .authorizeHttpRequests(authorize -> authorize.anyRequest().permitAll());

    return http.build();
  }
}
