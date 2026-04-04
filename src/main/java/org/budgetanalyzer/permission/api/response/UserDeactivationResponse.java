package org.budgetanalyzer.permission.api.response;

/** Response body for user deactivation. */
public record UserDeactivationResponse(
    String userId, String status, int rolesRemoved, boolean sessionsRevoked) {}
