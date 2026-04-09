package org.budgetanalyzer.permission.service.dto;

import java.util.List;

import org.budgetanalyzer.permission.api.response.UserReference;
import org.budgetanalyzer.permission.domain.User;

/** User detail plus assigned role IDs and dereferenced audit actors. */
public record UserDetail(
    User user, List<String> roleIds, UserReference deactivatedBy, UserReference deletedBy) {}
