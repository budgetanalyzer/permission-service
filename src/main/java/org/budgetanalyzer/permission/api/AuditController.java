package org.budgetanalyzer.permission.api;

import java.time.Instant;

import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.budgetanalyzer.permission.api.response.AuditLogResponse;
import org.budgetanalyzer.permission.service.AuditService;
import org.budgetanalyzer.permission.service.dto.AuditQueryFilter;
import org.budgetanalyzer.service.api.ApiErrorResponse;

/**
 * Controller for audit log access.
 *
 * <p>Provides read access to authorization audit logs for compliance and investigation.
 */
@Tag(name = "Audit", description = "Authorization audit logs for compliance and investigation")
@RestController
@RequestMapping("/v1/audit")
@PreAuthorize("hasAuthority('audit:read')")
public class AuditController {

  private final AuditService auditService;

  /**
   * Constructs a new AuditController.
   *
   * @param auditService the audit service
   */
  public AuditController(AuditService auditService) {
    this.auditService = auditService;
  }

  @Operation(
      summary = "Query audit log",
      description =
          "Queries authorization audit logs with optional filters for user and time range. "
              + "Requires 'audit:read' permission.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Audit logs retrieved successfully",
        content = @Content(schema = @Schema(implementation = AuditLogResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @GetMapping
  public Page<AuditLogResponse> getAuditLog(
      @Parameter(description = "Filter by user ID", example = "usr_abc123")
          @RequestParam(required = false)
          String userId,
      @Parameter(description = "Start of time range (ISO-8601)", example = "2024-01-01T00:00:00Z")
          @RequestParam(required = false)
          Instant startTime,
      @Parameter(description = "End of time range (ISO-8601)", example = "2024-12-31T23:59:59Z")
          @RequestParam(required = false)
          Instant endTime,
      @ParameterObject Pageable pageable) {
    var filter = new AuditQueryFilter(userId, startTime, endTime);
    var logs = auditService.queryAuditLog(filter, pageable);

    return logs.map(AuditLogResponse::from);
  }

  @Operation(
      summary = "Get audit log for specific user",
      description =
          "Returns all audit log entries for a specific user. "
              + "Requires 'audit:read' permission.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Audit logs retrieved successfully",
        content = @Content(schema = @Schema(implementation = AuditLogResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Insufficient permissions",
        content = @Content(schema = @Schema(implementation = ApiErrorResponse.class)))
  })
  @GetMapping("/users/{userId}")
  public Page<AuditLogResponse> getUserAudit(
      @Parameter(description = "User ID", example = "usr_abc123") @PathVariable String userId,
      @ParameterObject Pageable pageable) {
    var logs = auditService.queryByUser(userId, pageable);

    return logs.map(AuditLogResponse::from);
  }
}
