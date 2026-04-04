package org.budgetanalyzer.permission.service.dto;

/** Service-layer result for user deactivation. */
public record UserDeactivationResult(
    String userId, String status, int rolesRemoved, boolean sessionsRevoked) {}
