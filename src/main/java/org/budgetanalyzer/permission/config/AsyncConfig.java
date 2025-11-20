package org.budgetanalyzer.permission.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Configuration for asynchronous processing.
 *
 * <p>Enables @Async annotation support for audit logging and other non-blocking operations.
 */
@Configuration
@EnableAsync
public class AsyncConfig {
  // Optional: customize executor if needed
}
