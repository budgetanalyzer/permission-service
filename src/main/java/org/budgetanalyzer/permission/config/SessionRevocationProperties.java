package org.budgetanalyzer.permission.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Configuration properties for session revocation retry behavior.
 *
 * @param maxAttempts maximum number of revocation attempts
 * @param initialDelay delay before the first retry
 * @param multiplier backoff multiplier applied after each failed attempt
 * @param maxDelay upper bound on the computed delay between retries
 */
@ConfigurationProperties(prefix = "session-gateway.revocation")
public record SessionRevocationProperties(
    @DefaultValue("3") int maxAttempts,
    @DefaultValue("200ms") Duration initialDelay,
    @DefaultValue("2.0") double multiplier,
    @DefaultValue("2s") Duration maxDelay) {}
