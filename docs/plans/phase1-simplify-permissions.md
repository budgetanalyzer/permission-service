# Phase 1: Simplify permission-service schema and code

## Context

The permission-service has 63 Java classes, 8 database tables, and complex features (temporal tracking, delegations, cascading revocation, resource permissions, audit logging, Redis caching) that no downstream service uses. The goal is to simplify to clean RBAC with 2 roles (ADMIN, USER), ~25 classes, 5 tables, and add an internal endpoint the gateway will call to fetch permissions for JWT minting.

This is Phase 1 of a multi-phase plan where the session-gateway will eventually mint internal JWTs with permissions baked in. Phase 1 is isolated — it only touches permission-service and breaks nothing.

---

## Step 1: Rewrite database migrations

### V1__initial_schema.sql — rewrite to 5 tables

**File:** `src/main/resources/db/migration/V1__initial_schema.sql`

Keep `users` and `permissions` tables as-is. Simplify the other 3:

- **`roles`** — remove `parent_role_id` column and its index (`idx_roles_parent`). Keep soft delete fields and `is_system`.
- **`role_permissions`** — convert to simple join table: just `id BIGSERIAL PK`, `role_id`, `permission_id`, `created_at`. Remove temporal columns (`granted_at`, `granted_by`, `revoked_at`, `revoked_by`). Use `UNIQUE(role_id, permission_id)` instead of partial unique index.
- **`user_roles`** — convert to simple join table: just `id BIGSERIAL PK`, `user_id`, `role_id`, `created_at`. Remove `organization_id`, `granted_at`, `granted_by`, `expires_at`, `revoked_at`, `revoked_by`. Use `UNIQUE(user_id, role_id)` instead of partial unique index.
- **Remove** `resource_permissions`, `delegations`, `authorization_audit_log` table definitions entirely.

### V2__seed_default_data.sql — rewrite with 2 roles

**File:** `src/main/resources/db/migration/V2__seed_default_data.sql`

- Keep SYSTEM user insert.
- **2 roles:** `ADMIN` and `USER` (replace 6-role hierarchy).
- **Permissions (14):** `transactions:read/write/delete`, `accounts:read/write/delete`, `budgets:read/write/delete`, `users:read/write/delete`, `roles:read/write`, `audit:read`.
- **ADMIN** gets all 14 permissions.
- **USER** gets: `transactions:read/write`, `accounts:read/write`, `budgets:read/write`.
- Simplified role_permissions inserts — no `granted_at`/`granted_by` columns since they no longer exist.

---

## Step 2: Delete Java classes for removed features

### Domain (3 files — delete)
- `src/main/java/.../domain/Delegation.java`
- `src/main/java/.../domain/ResourcePermission.java`
- `src/main/java/.../domain/AuthorizationAuditLog.java`

### Repository (3 files — delete)
- `src/main/java/.../repository/DelegationRepository.java`
- `src/main/java/.../repository/ResourcePermissionRepository.java`
- `src/main/java/.../repository/AuditLogRepository.java`

### Service (5 files — delete)
- `src/main/java/.../service/DelegationService.java`
- `src/main/java/.../service/ResourcePermissionService.java`
- `src/main/java/.../service/CascadingRevocationService.java`
- `src/main/java/.../service/AuditService.java`
- `src/main/java/.../service/PermissionCacheService.java`

### Controller (3 files — delete)
- `src/main/java/.../api/DelegationController.java`
- `src/main/java/.../api/ResourcePermissionController.java`
- `src/main/java/.../api/AuditController.java`

### Request DTOs (2 files — delete)
- `src/main/java/.../api/request/DelegationRequest.java`
- `src/main/java/.../api/request/ResourcePermissionRequest.java`

### Response DTOs (4 files — delete)
- `src/main/java/.../api/response/DelegationResponse.java`
- `src/main/java/.../api/response/DelegationsResponse.java`
- `src/main/java/.../api/response/AuditLogResponse.java`
- `src/main/java/.../api/response/ResourcePermissionResponse.java`

### Service DTOs (2 files — delete)
- `src/main/java/.../service/dto/AuditQueryFilter.java`
- `src/main/java/.../service/dto/DelegationsSummary.java`

### Event (1 file — delete)
- `src/main/java/.../event/PermissionChangeEvent.java`

### Config (1 file — delete)
- `src/main/java/.../config/AsyncConfig.java`

### Exception (1 file — delete)
- `src/main/java/.../service/exception/ProtectedRoleException.java`

### Tests (5 files — delete)
- `src/test/java/.../service/DelegationServiceTest.java`
- `src/test/java/.../service/PermissionCacheServiceTest.java`
- `src/test/java/.../service/CascadingRevocationServiceTest.java`
- `src/test/java/.../service/PointInTimeQueryTest.java`
- `src/test/java/.../api/DelegationControllerTest.java`

**Total deletions: ~30 files**

---

## Step 3: Simplify remaining entities

### Role.java
- Remove `parentRoleId` field and its getter/setter.
- Remove the 4-arg constructor that takes `parentRoleId`.
- Keep `id`, `name`, `description`, `system` fields.

### UserRole.java
- Strip to simple join table entity. Remove fields: `organizationId`, `grantedAt`, `grantedBy`, `expiresAt`, `revokedAt`, `revokedBy`.
- Remove `isActive()` and `revoke()` methods.
- Keep: `id` (Long, auto-generated), `userId`, `roleId`. Keep extending `AuditableEntity` for `createdAt`/`updatedAt`.

### RolePermission.java
- Strip to simple join table entity. Remove fields: `grantedAt`, `grantedBy`, `revokedAt`, `revokedBy`.
- Remove `isActive()` and `revoke()` methods.
- Keep: `id` (Long, auto-generated), `roleId`, `permissionId`. Keep extending `AuditableEntity`.

---

## Step 4: Simplify repositories

### UserRoleRepository.java — rewrite
- Remove all temporal query methods: `findByUserIdAndRevokedAtIsNull`, `findByUserIdAndRoleIdAndRevokedAtIsNull`, `findRolesAtPointInTime`, `findActivePermissionIdsByUserId` (temporal version), `findActiveByUserId`, `findActiveByRoleId`.
- Replace with simple methods:
  - `List<UserRole> findByUserId(String userId)` — all roles for a user
  - `Optional<UserRole> findByUserIdAndRoleId(String userId, String roleId)` — check if assignment exists
  - `void deleteByUserIdAndRoleId(String userId, String roleId)` — hard delete on revocation
  - Custom JPQL: `findPermissionIdsByUserId(userId)` — joins user_roles → role_permissions → returns `Set<String>` permission IDs
  - Custom JPQL: `findRoleIdsByUserId(userId)` — returns `Set<String>` role IDs

### RolePermissionRepository.java — rewrite
- Remove all temporal query methods: `findByRoleIdAndRevokedAtIsNull`, `findActiveByRoleId`, `findActiveByPermissionId`.
- Replace with simple methods:
  - `List<RolePermission> findByRoleId(String roleId)` — permissions for a role
  - `void deleteByRoleId(String roleId)` — hard delete when role deleted

---

## Step 5: Simplify services

### PermissionService.java — major rewrite
- **Remove dependencies:** `ResourcePermissionRepository`, `DelegationRepository`, `AuditService`, `PermissionCacheService`.
- **Keep dependencies:** `UserRepository`, `UserRoleRepository`, `RoleRepository`, `RolePermissionRepository`.
- **`getEffectivePermissions(userId)`** — simplified: query user roles via `userRoleRepository.findRoleIdsByUserId()`, query permissions via `userRoleRepository.findPermissionIdsByUserId()`, return new `EffectivePermissions(roles, permissions)`.
- **`getUserRoles(userId)`** — keep similar logic but use `findByUserId()` instead of `findByUserIdAndRevokedAtIsNull()`.
- **`assignRole(userId, roleId, assignedBy)`** — simplify governance. Remove 3-tier system (BASIC_ROLES, ELEVATED_ROLES, PROTECTED_ROLE constants). Just validate user exists, role exists, assignment doesn't already exist. Use `userRoleRepository.save()`. No audit log, no cache invalidation.
- **`revokeRole(userId, roleId)`** — simplify. Find the UserRole, hard delete via `userRoleRepository.deleteByUserIdAndRoleId()`. No temporal revocation, no audit, no cache.
- **Remove:** `getPermissionsAtPointInTime()`, `getRolePermissionsAtPointInTime()`.

### RoleService.java — simplify
- **Remove dependency:** `CascadingRevocationService`.
- **Add dependency:** `UserRoleRepository`, `RolePermissionRepository` (for cleanup on delete).
- **`deleteRole(id, deletedBy)`** — hard delete user_roles and role_permissions for this role, then soft-delete the role.
- **`createRole(name, description)`** — remove `parentRoleId` parameter.
- **`updateRole(id, name, description)`** — remove `parentRoleId` parameter.

### UserService.java — simplify
- **Remove dependencies:** `CascadingRevocationService`, `AuditService`.
- **Add dependency:** `UserRoleRepository` (for cleanup on delete).
- **`deleteUser(id, deletedBy)`** — delete user_roles for user, then soft-delete. No audit logging.
- **`restoreUser(id)`** — keep as-is minus the audit log call.

### UserSyncService.java — minor changes
- **`assignDefaultRole()`** — remove `grantedAt`/`grantedBy` from UserRole creation since those fields no longer exist. Just set `userId` and `roleId`.

---

## Step 6: Simplify DTOs and request/response objects

### EffectivePermissions.java — rewrite
- Change from `record EffectivePermissions(Set<String> rolePermissions, List<ResourcePermission> resourcePermissions, List<Delegation> delegations)` to `record EffectivePermissions(Set<String> roles, Set<String> permissions)`.
- Remove `getAllPermissionIds()` — the `permissions` field is already the complete set.

### UserPermissionsResponse.java — rewrite
- Change from 3 fields (permissions, resourcePermissions, delegations) to just `Set<String> permissions`.
- Update `from(EffectivePermissions)` factory method.

### RoleResponse.java — simplify
- Remove `parentRoleId` field.
- Update `from(Role)` factory method.

### UserRoleAssignmentRequest.java — simplify
- Remove `organizationId` and `expiresAt` fields.
- Keep only `roleId`.

### RoleRequest.java — simplify
- Remove `parentRoleId` field.
- Keep `name` and `description`.

---

## Step 7: Update controllers

### UserPermissionController.java — minor updates
- Update `getCurrentUserPermissions()` and `getUserPermissions()` to work with simplified `UserPermissionsResponse.from()`.
- Update `assignRole()` — simplify the `@PreAuthorize` from `"hasAuthority('user-roles:assign-basic') or hasAuthority('user-roles:assign-elevated')"` to `"hasAuthority('roles:write')"` (simplified governance).
- Update `revokeRole()` `@PreAuthorize` from `"hasAuthority('user-roles:revoke')"` to `"hasAuthority('roles:write')"`.

### RoleController.java — minor updates
- Update `createRole()` — pass only `name`, `description` (no `parentRoleId`).
- Update `updateRole()` — same.

### SecurityExceptionHandler.java — remove ProtectedRoleException handler
- Remove the `handleProtectedRoleException()` method and its import since `ProtectedRoleException` is being deleted.

---

## Step 8: Add internal permissions endpoint

### New file: `src/main/java/.../api/InternalPermissionController.java`
- `GET /internal/v1/users/{auth0Sub}/permissions`
- Accepts `auth0Sub` as path variable, `email` and `displayName` as request parameters (for user creation on first login).
- Calls `userSyncService.syncUser(auth0Sub, email, displayName)` then `permissionService.getEffectivePermissions(user.getId())`.
- Returns new DTO: `InternalPermissionsResponse(String userId, Set<String> roles, Set<String> permissions)`.
- Secured with `@PreAuthorize("isAuthenticated()")` for now (the gateway will pass a valid JWT); refine to API key auth in Phase 2 if needed.

### New file: `src/main/java/.../api/response/InternalPermissionsResponse.java`
- `record InternalPermissionsResponse(String userId, Set<String> roles, Set<String> permissions)`

---

## Step 9: Remove Redis dependency

### build.gradle.kts
- Remove line: `implementation(libs.spring.boot.starter.data.redis)`

### application.yml
- Remove the `spring.data.redis` section (lines 39-42: host and port config).

---

## Step 10: Update tests

### TestConstants.java — simplify
- Remove `ROLE_ORG_ADMIN`, `ROLE_MANAGER`, `ROLE_ACCOUNTANT`, `ROLE_AUDITOR`. Keep `ROLE_USER` and change `ROLE_ADMIN` from `"SYSTEM_ADMIN"` to `"ADMIN"`.
- Remove `PERM_ASSIGN_BASIC`, `PERM_ASSIGN_ELEVATED`, `PERM_REVOKE`, `PERM_DELEGATIONS_WRITE`.
- Remove `TEST_MANAGER_ID`, `TEST_DELEGATEE_ID`.

### PermissionServiceTest.java — rewrite
- Update mocks to match simplified service (no ResourcePermissionRepository, DelegationRepository, AuditService, PermissionCacheService).
- Test simplified `getEffectivePermissions`, `assignRole`, `revokeRole`.
- Remove tests for governance tiers, cascading, point-in-time queries.

### UserSyncServiceTest.java — minor updates
- Update UserRole assertions to not check `grantedAt`/`grantedBy`.

### UserPermissionControllerTest.java — update
- Update expected response format (just permissions set, no resourcePermissions/delegations).

### RoleControllerTest.java — update
- Update to not pass `parentRoleId` in requests/responses.

### Add InternalPermissionControllerTest.java
- Test the new `/internal/v1/users/{auth0Sub}/permissions` endpoint.
- Test user creation on first call, existing user on second call.
- Test correct roles and permissions returned.

---

## Step 11: Update CLAUDE.md

Update the project documentation to reflect the simplified model:
- Remove references to delegations, resource permissions, audit logging, temporal tracking, Redis caching.
- Update role tier table to just ADMIN/USER.
- Update entity list.
- Update package structure description.
- Remove delegation and temporal pattern sections.

---

## File Change Summary

| Action | Count | Details |
|--------|-------|---------|
| Delete | ~30 | Domain(3), Repo(3), Service(5), Controller(3), Request DTO(2), Response DTO(4), Service DTO(2), Event(1), Config(1), Exception(1), Tests(5) |
| Rewrite | 2 | V1 migration, V2 migration |
| Simplify | ~15 | Role, UserRole, RolePermission, UserRoleRepo, RolePermissionRepo, PermissionService, RoleService, UserService, UserSyncService, EffectivePermissions, UserPermissionsResponse, RoleResponse, UserRoleAssignmentRequest, RoleRequest, SecurityExceptionHandler |
| Modify | ~5 | UserPermissionController, RoleController, build.gradle.kts, application.yml, TestConstants |
| Create | ~3 | InternalPermissionController, InternalPermissionsResponse, InternalPermissionControllerTest |
| Update tests | ~4 | PermissionServiceTest, UserSyncServiceTest, UserPermissionControllerTest, RoleControllerTest |

**Result:** ~25 Java classes (down from ~51), 5 tables (down from 8), 3 controllers, 4 services.

---

## Verification

1. `./gradlew clean build` — compiles and all tests pass
2. `./gradlew clean spotlessApply` — code formatted
3. Start service with H2 (test profile) — Flyway runs both migrations successfully
4. Verify no references remain to deleted classes (no compilation errors)
5. Verify internal endpoint returns correct structure: `{ userId, roles, permissions }`

## Execution Order

Execute steps 1-2 first (migrations + deletions), then 3-7 (simplify remaining code) in dependency order (entities → repos → services → DTOs → controllers), then 8 (new endpoint), then 9 (Redis removal), then 10-11 (tests + docs). Build and verify after each major step.
