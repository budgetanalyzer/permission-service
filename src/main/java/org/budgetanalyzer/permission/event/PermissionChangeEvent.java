package org.budgetanalyzer.permission.event;

import java.util.Map;

import org.budgetanalyzer.permission.domain.Delegation;
import org.budgetanalyzer.permission.domain.ResourcePermission;
import org.budgetanalyzer.permission.domain.User;

/**
 * Event representing a change in permissions.
 *
 * <p>Used for audit logging and cache invalidation. Provides factory methods for common permission
 * change scenarios.
 */
public class PermissionChangeEvent {

  private final String userId;
  private final String action;
  private final Map<String, String> context;

  private PermissionChangeEvent(String userId, String action, Map<String, String> context) {
    this.userId = userId;
    this.action = action;
    this.context = context;
  }

  public String getUserId() {
    return userId;
  }

  public String getAction() {
    return action;
  }

  public Map<String, String> getContext() {
    return context;
  }

  // Factory methods for role operations

  public static PermissionChangeEvent roleAssigned(String userId, String roleId, String grantedBy) {
    return new PermissionChangeEvent(
        userId, "ROLE_ASSIGNED", Map.of("roleId", roleId, "grantedBy", grantedBy));
  }

  public static PermissionChangeEvent roleRevoked(String userId, String roleId, String revokedBy) {
    return new PermissionChangeEvent(
        userId, "ROLE_REVOKED", Map.of("roleId", roleId, "revokedBy", revokedBy));
  }

  // Factory methods for cascading operations

  public static PermissionChangeEvent cascadingRevocation(
      String entityType, String entityId, String revokedBy) {
    return new PermissionChangeEvent(
        entityId,
        "CASCADING_REVOCATION",
        Map.of("entityType", entityType, "entityId", entityId, "revokedBy", revokedBy));
  }

  // Factory methods for delegation operations

  public static PermissionChangeEvent delegationCreated(Delegation delegation) {
    return new PermissionChangeEvent(
        delegation.getDelegateeId(),
        "DELEGATION_CREATED",
        Map.of(
            "delegationId", String.valueOf(delegation.getId()),
            "delegatorId", delegation.getDelegatorId(),
            "scope", delegation.getScope()));
  }

  public static PermissionChangeEvent delegationRevoked(Delegation delegation) {
    return new PermissionChangeEvent(
        delegation.getDelegateeId(),
        "DELEGATION_REVOKED",
        Map.of(
            "delegationId", String.valueOf(delegation.getId()),
            "delegatorId", delegation.getDelegatorId()));
  }

  // Factory methods for user operations

  public static PermissionChangeEvent userDeleted(User user, String deletedBy) {
    return new PermissionChangeEvent(user.getId(), "USER_DELETED", Map.of("deletedBy", deletedBy));
  }

  public static PermissionChangeEvent userRestored(User user) {
    return new PermissionChangeEvent(user.getId(), "USER_RESTORED", Map.of());
  }

  // Factory methods for resource permission operations

  public static PermissionChangeEvent resourcePermissionGranted(ResourcePermission permission) {
    return new PermissionChangeEvent(
        permission.getUserId(),
        "RESOURCE_PERMISSION_GRANTED",
        Map.of(
            "permissionId", String.valueOf(permission.getId()),
            "resourceType", permission.getResourceType(),
            "resourceId", permission.getResourceId(),
            "permission", permission.getPermission()));
  }

  public static PermissionChangeEvent resourcePermissionRevoked(ResourcePermission permission) {
    return new PermissionChangeEvent(
        permission.getUserId(),
        "RESOURCE_PERMISSION_REVOKED",
        Map.of(
            "permissionId", String.valueOf(permission.getId()),
            "resourceType", permission.getResourceType(),
            "resourceId", permission.getResourceId()));
  }
}
