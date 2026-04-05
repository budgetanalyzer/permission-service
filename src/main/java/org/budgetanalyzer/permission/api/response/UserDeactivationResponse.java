package org.budgetanalyzer.permission.api.response;

import org.budgetanalyzer.permission.domain.UserStatus;

/** Response body for user deactivation. */
public record UserDeactivationResponse(
    String userId, UserStatus status, int rolesRemoved, boolean sessionsRevoked) {}
