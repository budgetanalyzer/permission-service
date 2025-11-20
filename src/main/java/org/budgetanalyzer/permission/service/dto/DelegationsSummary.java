package org.budgetanalyzer.permission.service.dto;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

import org.budgetanalyzer.permission.domain.Delegation;

/**
 * Contains both delegations given by and received by a user.
 *
 * <p>Used by DelegationService, transformed to DelegationsResponse by controller.
 */
@Schema(description = "Internal DTO containing delegations summary for a user")
public record DelegationsSummary(
    @Schema(description = "Delegations created by this user") List<Delegation> given,
    @Schema(description = "Delegations received by this user") List<Delegation> received) {}
