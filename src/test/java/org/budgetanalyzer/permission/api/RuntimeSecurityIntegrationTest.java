package org.budgetanalyzer.permission.api;

import static com.github.tomakehurst.wiremock.client.WireMock.delete;
import static com.github.tomakehurst.wiremock.client.WireMock.noContent;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import org.budgetanalyzer.permission.TestConstants;
import org.budgetanalyzer.permission.service.PermissionServiceIntegrationTestSupport;
import org.budgetanalyzer.service.audit.AuditLogger;
import org.budgetanalyzer.service.security.test.ClaimsHeaderTestBuilder;

@AutoConfigureMockMvc
@TestPropertySource(properties = "logging.level.budgetanalyzer.audit=INFO")
class RuntimeSecurityIntegrationTest extends PermissionServiceIntegrationTestSupport {

  private static final String REVOCATION_PATH =
      "/session-gateway/internal/v1/sessions/users/" + TestConstants.TEST_USER_ID;
  private static final Set<String> AUTHORIZATION_EXCEPTIONS =
      Set.of(
          "org.budgetanalyzer.permission.api.InternalPermissionController"
              + "#getUserAuthorization(java.lang.String)",
          "org.budgetanalyzer.permission.api.InternalPermissionController"
              + "#syncUser(org.budgetanalyzer.permission.api.request.UserSyncRequest)");

  private final ch.qos.logback.classic.Logger auditLogbackLogger =
      (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(AuditLogger.LOGGER_NAME);
  private final ListAppender<ILoggingEvent> listAppender = new ListAppender<>();

  @Autowired private RequestMappingHandlerMapping requestMappingHandlerMapping;
  @Autowired private MockMvc mockMvc;

  @BeforeEach
  void attachAuditAppender() {
    listAppender.start();
    auditLogbackLogger.addAppender(listAppender);
  }

  @AfterEach
  void detachAuditAppender() {
    auditLogbackLogger.detachAppender(listAppender);
    listAppender.stop();
  }

  @Test
  void allApplicationHandlersDeclareAuthorizationPolicy() {
    var applicationHandlers =
        requestMappingHandlerMapping.getHandlerMethods().values().stream()
            .filter(RuntimeSecurityIntegrationTest::isApplicationHandler)
            .toList();
    var handlerIdentifiers =
        applicationHandlers.stream()
            .map(RuntimeSecurityIntegrationTest::handlerIdentifier)
            .collect(Collectors.toSet());
    var handlersWithoutAuthorization =
        applicationHandlers.stream()
            .filter(
                handlerMethod ->
                    !AUTHORIZATION_EXCEPTIONS.contains(handlerIdentifier(handlerMethod)))
            .filter(handlerMethod -> !hasPreAuthorize(handlerMethod))
            .map(RuntimeSecurityIntegrationTest::handlerIdentifier)
            .sorted()
            .toList();

    assertThat(handlerIdentifiers).containsAll(AUTHORIZATION_EXCEPTIONS);
    assertThat(handlersWithoutAuthorization)
        .as("application request handlers without @PreAuthorize")
        .isEmpty();
  }

  @Test
  void stateChangingRequestEmitsExactlyOneAuditEvent() throws Exception {
    persistUser(
        TestConstants.TEST_ADMIN_ID,
        TestConstants.TEST_IDP_SUB_ADMIN,
        "admin@example.com",
        "Admin User");
    persistUser(
        TestConstants.TEST_USER_ID,
        TestConstants.TEST_IDP_SUB,
        TestConstants.TEST_EMAIL,
        TestConstants.TEST_DISPLAY_NAME);
    wireMockServer.stubFor(delete(urlEqualTo(REVOCATION_PATH)).willReturn(noContent()));

    mockMvc
        .perform(
            post("/v1/users/{id}/deactivate", TestConstants.TEST_USER_ID)
                .with(
                    ClaimsHeaderTestBuilder.user(TestConstants.TEST_ADMIN_ID)
                        .withPermissions(TestConstants.PERM_USERS_WRITE)))
        .andExpect(status().isOk());

    assertThat(listAppender.list)
        .singleElement()
        .extracting(ILoggingEvent::getFormattedMessage)
        .asString()
        .contains("\"actor\":\"" + TestConstants.TEST_ADMIN_ID + "\"")
        .contains("\"operation\":\"POST /v1/users/{id}/deactivate\"");
  }

  private static boolean isApplicationHandler(HandlerMethod handlerMethod) {
    return handlerMethod.getBeanType().getPackageName().startsWith("org.budgetanalyzer");
  }

  private static boolean hasPreAuthorize(HandlerMethod handlerMethod) {
    return AnnotatedElementUtils.findMergedAnnotation(handlerMethod.getMethod(), PreAuthorize.class)
            != null
        || AnnotatedElementUtils.findMergedAnnotation(
                handlerMethod.getBeanType(), PreAuthorize.class)
            != null;
  }

  private static String handlerIdentifier(HandlerMethod handlerMethod) {
    var parameterTypes =
        Arrays.stream(handlerMethod.getMethod().getParameterTypes())
            .map(Class::getName)
            .collect(Collectors.joining(","));
    return handlerMethod.getBeanType().getName()
        + "#"
        + handlerMethod.getMethod().getName()
        + "("
        + parameterTypes
        + ")";
  }
}
