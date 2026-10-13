package org.budgetanalyzer.permission.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.NullSecurityContextRepository;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import org.springframework.security.web.util.matcher.OrRequestMatcher;

/**
 * Service-owned security overrides for permission-service.
 *
 * <p>The internal permissions and authorization GET endpoints are called by session-gateway before
 * claims headers exist for the user request. These exceptions remain method-and-path-scoped here
 * instead of inside service-common so the shared library does not grant anonymous access to every
 * {@code /internal/**} route. Runtime caller restriction is enforced by orchestration through mesh
 * identity and authorization policy.
 */
@Configuration
public class PermissionServiceSecurityConfig {

  private static final String INTERNAL_PERMISSIONS_ENDPOINT = "/internal/v1/users/*/permissions";
  private static final String INTERNAL_AUTHORIZATION_ENDPOINT =
      "/internal/v1/users/*/authorization";

  /**
   * Permits the internal permission lookup GET endpoints without claims headers.
   *
   * @param http the servlet security builder
   * @return the configured filter chain
   * @throws Exception if the chain cannot be built
   */
  @Bean
  @Order(0)
  public SecurityFilterChain internalPermissionsSecurityFilterChain(HttpSecurity http)
      throws Exception {
    var requestMatcherBuilder = PathPatternRequestMatcher.withDefaults();
    var internalPermissionsRequestMatcher =
        requestMatcherBuilder.matcher(HttpMethod.GET, INTERNAL_PERMISSIONS_ENDPOINT);
    var internalAuthorizationRequestMatcher =
        requestMatcherBuilder.matcher(HttpMethod.GET, INTERNAL_AUTHORIZATION_ENDPOINT);

    http.securityMatcher(
            new OrRequestMatcher(
                internalPermissionsRequestMatcher, internalAuthorizationRequestMatcher))
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
