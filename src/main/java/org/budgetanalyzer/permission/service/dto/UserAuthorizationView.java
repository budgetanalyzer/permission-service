package org.budgetanalyzer.permission.service.dto;

import java.util.Set;

import org.budgetanalyzer.permission.domain.UserStatus;

/** Contains the current status and effective authorization grants for a user. */
public record UserAuthorizationView(
    String userId, UserStatus status, Set<String> roles, Set<String> permissions) {}
