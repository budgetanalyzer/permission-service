package org.budgetanalyzer.permission.api.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import io.swagger.v3.oas.annotations.media.Schema;

/** Request body for the login-time user sync called by Session Gateway. */
@Schema(description = "Identity provider profile data for login-time user sync")
public record UserSyncRequest(
    @Schema(
            description =
                "Identity provider subject identifier (OIDC sub claim, provider-agnostic)",
            example = "auth0|abc123",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank
        String idpSub,
    @Schema(
            description = "User email",
            example = "user@example.com",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        String email,
    @Schema(
            description = "User display name",
            example = "Example User",
            requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull
        String displayName) {}
