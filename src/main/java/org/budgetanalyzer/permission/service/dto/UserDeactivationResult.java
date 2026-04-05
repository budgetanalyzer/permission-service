package org.budgetanalyzer.permission.service.dto;

import org.budgetanalyzer.permission.domain.UserStatus;

/** Service-layer result for user deactivation. */
public record UserDeactivationResult(
    String userId, UserStatus status, int rolesRemoved, boolean sessionsRevoked) {}
