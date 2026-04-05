package org.budgetanalyzer.permission.client;

/**
 * Result of a session revocation attempt.
 *
 * @param revoked true if all sessions were revoked successfully
 * @param retryExhausted true if revocation failed after exhausting all retry attempts
 */
public record SessionRevocationResult(boolean revoked, boolean retryExhausted) {}
