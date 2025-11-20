package org.budgetanalyzer.permission.api.response;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.service.dto.DelegationsSummary;

/** Response DTO for combined given/received delegations. */
public record DelegationsResponse(
    @Schema(description = "Delegations created by this user") List<DelegationResponse> given,
    @Schema(description = "Delegations received by this user") List<DelegationResponse> received) {
  /**
   * Creates a DelegationsResponse from DelegationsSummary.
   *
   * @param summary the delegations summary from service layer
   * @return the response DTO
   */
  public static DelegationsResponse from(DelegationsSummary summary) {
    return new DelegationsResponse(
        summary.given().stream().map(DelegationResponse::from).toList(),
        summary.received().stream().map(DelegationResponse::from).toList());
  }
}
