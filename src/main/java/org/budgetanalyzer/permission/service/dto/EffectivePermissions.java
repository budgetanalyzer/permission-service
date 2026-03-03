package org.budgetanalyzer.permission.service.dto;

import java.util.Set;

/** Contains a user's effective permissions from role assignments. */
public record EffectivePermissions(Set<String> roles, Set<String> permissions) {}
