package org.budgetanalyzer.permission.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.budgetanalyzer.permission.service.PermissionServiceIntegrationTestSupport;

@AutoConfigureMockMvc
class PermissionOpenApiIntegrationTest extends PermissionServiceIntegrationTestSupport {

  private static final String API_ERROR_RESPONSE_REFERENCE =
      "#/components/schemas/ApiErrorResponse";

  @Autowired private MockMvc mockMvc;

  @Autowired private JsonMapper jsonMapper;

  @Test
  void shouldGenerateExpectedUserApiSecurityDocumentation() throws Exception {
    var openApiJsonNode = readOpenApiDocument();

    assertSecurityResponses(openApiJsonNode.at("/paths/~1v1~1users/get"));
    assertSecurityResponses(openApiJsonNode.at("/paths/~1v1~1users~1{id}/get"));
    assertSecurityResponses(openApiJsonNode.at("/paths/~1v1~1users~1{id}~1deactivate/post"));
    var internalPermissionOperationJsonNode =
        openApiJsonNode.at("/paths/~1internal~1v1~1users~1{idpSub}~1permissions/get");
    var internalAuthorizationOperationJsonNode =
        openApiJsonNode.at("/paths/~1internal~1v1~1users~1{userId}~1authorization/get");

    assertThat(internalPermissionOperationJsonNode.isMissingNode()).isFalse();
    assertThat(internalPermissionOperationJsonNode.at("/responses/401").isMissingNode()).isTrue();
    assertThat(internalPermissionOperationJsonNode.at("/responses/403").isMissingNode()).isTrue();
    assertThat(internalAuthorizationOperationJsonNode.isMissingNode()).isFalse();
    assertThat(internalAuthorizationOperationJsonNode.at("/responses/401").isMissingNode())
        .isTrue();
    assertThat(internalAuthorizationOperationJsonNode.at("/responses/403").isMissingNode())
        .isTrue();
    assertApiErrorResponseReference(internalAuthorizationOperationJsonNode, "404");
    assertThat(openApiJsonNode.at("/paths/~1v1~1users/get/responses/400/description").asString())
        .isEqualTo("Invalid request");
    assertThat(
            openApiJsonNode.at("/paths/~1v1~1users~1{id}/get/responses/404/description").asString())
        .isEqualTo("User not found");
    assertThat(
            openApiJsonNode
                .at("/paths/~1v1~1users~1{id}~1deactivate/post/responses/503/description")
                .asString())
        .isEqualTo("Session revocation failed after retry; user was deactivated, retry is safe");
  }

  private JsonNode readOpenApiDocument() throws Exception {
    var responseBody =
        mockMvc
            .perform(get("/permission-service/v3/api-docs").contextPath("/permission-service"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return jsonMapper.readTree(responseBody);
  }

  private void assertSecurityResponses(JsonNode operationJsonNode) {
    assertThat(operationJsonNode.isMissingNode()).isFalse();
    assertApiErrorResponseReference(operationJsonNode, "401");
    assertApiErrorResponseReference(operationJsonNode, "403");
  }

  private void assertApiErrorResponseReference(JsonNode operationJsonNode, String responseCode) {
    assertThat(
            operationJsonNode
                .at("/responses/" + responseCode + "/content/application~1json/schema/$ref")
                .asString())
        .isEqualTo(API_ERROR_RESPONSE_REFERENCE);
  }
}
