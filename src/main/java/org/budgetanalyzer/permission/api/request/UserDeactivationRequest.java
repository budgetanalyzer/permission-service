package org.budgetanalyzer.permission.api.request;

import jakarta.validation.constraints.NotBlank;

/** Request body for user deactivation. */
public record UserDeactivationRequest(
    @NotBlank(message = "Deactivated-by user ID is required") String deactivatedBy) {}
