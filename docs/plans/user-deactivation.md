# Permission-Service: User Deactivation Implementation Plan

**Date:** 2026-04-04
**Status:** Draft
**Parent plan:** [cross-service-user-revocation](/workspace/architecture-conversations/docs/plans/cross-service-user-revocation.md)

## Context

The Budget Analyzer system needs the ability to immediately ban a user from the system. The
cross-service user revocation plan defines the full control flow: permission-service marks the user
as deactivated (the durable gate), removes role assignments, and calls Session Gateway to kill
active sessions. A deactivated user cannot log in again because `syncUser()` detects the deactivated
status and returns non-2xx to Session Gateway.

This plan covers the permission-service work only. Session Gateway and orchestration changes are
tracked separately.

## Design Decisions

**Deactivation logic location**: `UserService` — it already owns `deleteUser()` which does the same
find-user/remove-roles/change-state pattern. Adding `deactivateUser()` keeps user lifecycle
operations together.

**Login-gate query strategy**: Add `existsByIdpSubAndStatus(String, String)` as a guard clause in
`syncUser()`, keeping the existing `findByIdpSubAndDeletedFalse()` for the create-or-update path.
The alternative of `Optional<User> findByIdpSub()` has a multi-result bug — the partial unique index
`users_idp_sub_active` only constrains `deleted=false` rows, so a soft-deleted row + active row for
the same `idp_sub` would cause `IncorrectResultSizeDataAccessException`. Because this guard must
also match soft-deleted rows, it needs its own non-partial lookup index on `(idp_sub, status)`;
the existing `deleted=false` indexes cannot satisfy it.

**Exception class**: `UserDeactivatedException extends BusinessException` → HTTP 422 with code
`USER_DEACTIVATED`. This is a business rule violation (the user exists but is barred), and 422 is
non-2xx which satisfies the Session Gateway contract.

**Security config**: Widen the `@Order(0)` filter chain path from `/internal/v1/users/*/permissions`
to `/internal/v1/users/**`. Both the permissions endpoint and deactivation endpoint are internal,
share the same trust model, and are network-restricted by mesh policy. Only
`InternalPermissionController` lives under `/internal/v1/users`.

**Deactivation commit boundary**: The user status change and role removal must commit before the
Session Gateway call starts. `UserService.deactivateUser()` therefore suspends the class-level
read-only transaction, executes the database mutation in an explicit transaction, and only then
calls Session Gateway. Failure still returns `false` and never blocks deactivation; PostgreSQL
remains the durable gate even if the network call is slow or unavailable.

**Idempotency**: Deactivating an already-deactivated user returns 200 with `rolesRemoved=0`. Session
revocation is still attempted (retries after partial failure should actually revoke sessions).

**Status as string, not enum**: The cross-service plan specifies varchar(20). Two values now
(`ACTIVE`, `DEACTIVATED`); adding future states (e.g., `SUSPENDED`) is a code change, not a
migration.

---

## Implementation Steps

### Step 1 — Flyway Migrations

**Create**: `src/main/resources/db/migration/V4__add_user_deactivation_fields.sql`

```sql
ALTER TABLE users ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE';
ALTER TABLE users ADD COLUMN deactivated_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE users ADD COLUMN deactivated_by VARCHAR(50);

COMMENT ON COLUMN users.status IS 'User access control state: ACTIVE, DEACTIVATED';
COMMENT ON COLUMN users.deactivated_at IS 'Timestamp when user was deactivated';
COMMENT ON COLUMN users.deactivated_by IS 'User ID who triggered deactivation';
```

Existing rows get `status='ACTIVE'` via default. Nullable `deactivated_at`/`deactivated_by` don't
break existing queries.

The lookup index is included in the same migration:

```sql
CREATE INDEX idx_users_idp_sub_status ON users(idp_sub, status);
```

This keeps `existsByIdpSubAndStatus(...)` on the login and refresh path off a full table scan while
still allowing deactivated + soft-deleted rows to match.

---

### Step 2 — User Entity Changes

**Modify**: `src/main/java/org/budgetanalyzer/permission/domain/User.java`

Add fields:

```java
@Column(name = "status", nullable = false, length = 20)
private String status = "ACTIVE";

@Column(name = "deactivated_at")
private Instant deactivatedAt;

@Column(name = "deactivated_by", length = 50)
private String deactivatedBy;
```

Add methods:
- `deactivate(String deactivatedBy)` — sets status to `"DEACTIVATED"`, records timestamp and actor
- `isDeactivated()` — returns `"DEACTIVATED".equals(status)`
- Standard getters for all three fields

Import: `java.time.Instant`

---

### Step 3 — Repository Changes

**Modify**: `src/main/java/org/budgetanalyzer/permission/repository/UserRepository.java`

Add:

```java
boolean existsByIdpSubAndStatus(String idpSub, String status);
```

Spring Data derives: `SELECT EXISTS(SELECT 1 FROM users WHERE idp_sub = ? AND status = ?)`. Returns
boolean — no multi-result issue. Handles the deactivated+soft-deleted edge case (matches regardless
of `deleted` flag). The V5 composite index supports this lookup because the existing partial
`deleted=false` indexes do not apply.

**Modify**: `src/main/java/org/budgetanalyzer/permission/repository/UserRoleRepository.java`

Change return type:

```java
// was: void deleteByUserId(String userId);
int deleteByUserId(String userId);
```

Backward-compatible — existing callers (`UserService.deleteUser`) ignore the return value.

---

### Step 4 — Exception Class

**Create**: `src/main/java/org/budgetanalyzer/permission/service/exception/UserDeactivatedException.java`

Extends `BusinessException` (from service-common). Constructor takes `idpSub`, passes message and
error code `USER_DEACTIVATED`. Follows the `DuplicateRoleAssignmentException` pattern exactly.

---

### Step 5 — Login-Gate Change in UserSyncService

**Modify**: `src/main/java/org/budgetanalyzer/permission/service/UserSyncService.java`

Add a guard clause at the top of `syncUser()`:

```java
public User syncUser(String idpSub, String email, String displayName) {
    if (userRepository.existsByIdpSubAndStatus(idpSub, "DEACTIVATED")) {
        throw new UserDeactivatedException(idpSub);
    }
    return userRepository
        .findByIdpSubAndDeletedFalse(idpSub)
        .map(user -> updateUser(user, email, displayName))
        .orElseGet(() -> createUser(idpSub, email, displayName));
}
```

**Why this approach**: The `existsBy` query checks ALL rows (deleted or not) for
`status=DEACTIVATED`. If a user was deactivated and later soft-deleted (the "banned and archived"
case from the cross-service plan), the guard still catches them. The existing
`findByIdpSubAndDeletedFalse` handles the normal create-or-update path unchanged.

**Behavioral change**:

| State | Old behavior | New behavior |
|-------|-------------|-------------|
| No user | Create | Create (unchanged) |
| Active, not deleted | Update | Update (unchanged) |
| Soft-deleted only | Create new | Create new (unchanged) |
| Deactivated (deleted=false) | N/A | **Throw `UserDeactivatedException`** |
| Deactivated + soft-deleted | N/A | **Throw `UserDeactivatedException`** |

---

### Step 6 — Session Gateway Client

**Modify**: `src/main/resources/application.yml`

Add under root:

```yaml
session-gateway:
  base-url: ${SESSION_GATEWAY_BASE_URL:http://localhost:8085/session-gateway}
```

**Modify**: `src/main/java/org/budgetanalyzer/permission/config/PermissionServiceConfig.java`

Add `RestClient` bean:

```java
@Bean
public RestClient sessionGatewayRestClient(
        @Value("${session-gateway.base-url}") String baseUrl) {
    return RestClient.builder().baseUrl(baseUrl).build();
}
```

**Create**: `src/main/java/org/budgetanalyzer/permission/client/SessionGatewayClient.java`

- `@Component` with constructor-injected `RestClient sessionGatewayRestClient`
- `revokeUserSessions(String userId)` → calls `DELETE /internal/v1/sessions/users/{userId}`
- Returns `boolean` (true = success, false = any failure)
- Catches `Exception` broadly — any failure mode returns false, logs error
- Never throws — the caller reads the boolean flag

---

### Step 7 — Deactivation DTOs

**Create**: `src/main/java/org/budgetanalyzer/permission/api/request/UserDeactivationRequest.java`

```java
public record UserDeactivationRequest(
    @NotBlank(message = "Deactivated-by user ID is required") String deactivatedBy) {}
```

Request body because this is an internal endpoint with no SecurityContext — the caller must identify
the actor. Follows `UserRoleAssignmentRequest` pattern.

**Create**: `src/main/java/org/budgetanalyzer/permission/api/response/UserDeactivationResponse.java`

```java
public record UserDeactivationResponse(
    String userId, String status, int rolesRemoved, boolean sessionsRevoked) {}
```

**Create**: `src/main/java/org/budgetanalyzer/permission/service/dto/UserDeactivationResult.java`

Same shape as response — service layer returns a domain result, controller maps to API response.
Follows existing `EffectivePermissions` pattern in `service/dto/`.

---

### Step 8 — Deactivation Service Logic

**Modify**: `src/main/java/org/budgetanalyzer/permission/service/UserService.java`

Add `SessionGatewayClient` dependency (constructor injection — updates existing constructor).

Add method:

```java
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public UserDeactivationResult deactivateUser(String userId, String deactivatedBy) {
    var persistedUserDeactivation = Objects.requireNonNull(
        transactionTemplate.execute(
            transactionStatus -> persistUserDeactivation(userId, deactivatedBy)),
        "User deactivation transaction returned null");

    var sessionsRevoked = sessionGatewayClient.revokeUserSessions(userId);

    return new UserDeactivationResult(
        persistedUserDeactivation.userId(),
        persistedUserDeactivation.status(),
        persistedUserDeactivation.rolesRemoved(),
        sessionsRevoked);
}
```

**Key behaviors**:
- Uses `findById` (not `findByIdActive`) to see deactivated users for idempotency
- Soft-deleted users are treated as not found (404)
- Already-deactivated users skip state change but still attempt session revocation (retry support)
- Database state commits before session revocation begins
- Session revocation always attempted, failure → `sessionsRevoked=false`

---

### Step 9 — Deactivation Endpoint

**Modify**: `src/main/java/org/budgetanalyzer/permission/api/InternalPermissionController.java`

Add `UserService` dependency (update constructor).

Add endpoint:

```java
@PostMapping("/{userId}/deactivate")
public UserDeactivationResponse deactivateUser(
        @PathVariable String userId,
        @Valid @RequestBody UserDeactivationRequest request) {
    var result = userService.deactivateUser(userId, request.deactivatedBy());
    return new UserDeactivationResponse(
        result.userId(), result.status(), result.rolesRemoved(), result.sessionsRevoked());
}
```

Returns 200 with body (not 204 — the response carries partial failure information).

---

### Step 10 — Security Config Update

**Modify**: `src/main/java/org/budgetanalyzer/permission/config/PermissionServiceSecurityConfig.java`

Change:

```java
private static final String INTERNAL_PERMISSIONS_ENDPOINT = "/internal/v1/users/*/permissions";
```

To:

```java
private static final String INTERNAL_USERS_ENDPOINT = "/internal/v1/users/**";
```

Update the `securityMatcher` to use this new constant. Both the GET permissions and POST deactivate
endpoints are internal with the same trust model. Only `InternalPermissionController` maps under this
path — `UserPermissionController` uses `/v1/users` (no `/internal/` prefix).

---

### Step 11 — Tests

**Modify**: `src/test/java/org/budgetanalyzer/permission/service/UserSyncServiceTest.java`

Add nested class for deactivation gate tests:

| Test | Mock setup | Assertion |
|------|-----------|-----------|
| `shouldThrowWhenUserIsDeactivated` | `existsByIdpSubAndStatus` returns true | Throws `UserDeactivatedException` |
| `shouldCreateUserWhenNotDeactivated` | `existsByIdpSubAndStatus` returns false, `findByIdpSubAndDeletedFalse` returns empty | Creates user normally |
| `shouldUpdateActiveUserWhenNotDeactivated` | `existsByIdpSubAndStatus` returns false, `findByIdpSubAndDeletedFalse` returns user | Updates user normally |

Existing tests need one update: add `when(userRepository.existsByIdpSubAndStatus(...)).thenReturn(false)`
stubs since `syncUser` now calls this method before the existing logic.

**Create**: `src/test/java/org/budgetanalyzer/permission/service/UserServiceTest.java`

New file (doesn't exist yet). Nested classes for `deactivateUser`:

| Test | Mock setup | Assertion |
|------|-----------|-----------|
| `shouldDeactivateActiveUser` | `findById` returns active user, client returns true | User deactivated, roles deleted, result correct |
| `shouldBeIdempotentForDeactivatedUser` | `findById` returns deactivated user, client returns true | No save, no role deletion, `rolesRemoved=0` |
| `shouldThrowWhenUserNotFound` | `findById` returns empty | `ResourceNotFoundException` |
| `shouldThrowWhenUserIsSoftDeleted` | `findById` returns deleted user | `ResourceNotFoundException` |
| `shouldReportPartialFailure` | `findById` returns active user, client returns false | Deactivation committed, `sessionsRevoked=false` |

Use `ArgumentCaptor` to verify the User entity state passed to `save()`.

**Create**: `src/test/java/org/budgetanalyzer/permission/client/SessionGatewayClientTest.java`

Use Mockito to mock the `RestClient` fluent chain:

| Test | Mock behavior | Assertion |
|------|--------------|-----------|
| `shouldReturnTrueOnSuccess` | Chain completes normally | Returns `true` |
| `shouldReturnFalseOnServerError` | `retrieve()` throws | Returns `false` |
| `shouldReturnFalseOnConnectionFailure` | `delete()` throws | Returns `false` |

**Modify**: `src/test/java/org/budgetanalyzer/permission/api/InternalPermissionControllerTest.java`

Add tests for the deactivation endpoint and the login gate:

| Test | Setup | Expected |
|------|-------|----------|
| `shouldDeactivateUser` | Service returns full result | 200 with JSON body |
| `shouldReturn404WhenUserNotFound` | Service throws `ResourceNotFoundException` | 404 |
| `shouldReturn400WhenDeactivatedByIsBlank` | Invalid request body | 400 |
| `shouldReturn422WhenUserIsDeactivated` (login gate) | `syncUser` throws `UserDeactivatedException` | 422 with `USER_DEACTIVATED` code |

Add `@MockitoBean UserService` and `@MockitoBean SessionGatewayClient` to test class.

**Modify**: `src/test/java/org/budgetanalyzer/permission/TestConstants.java`

Add constant: `TEST_DEACTIVATED_BY = "usr_admin456"` (reuse `TEST_ADMIN_ID`).

---

## File Summary

| # | File | Action |
|---|------|--------|
| 1 | `src/main/resources/db/migration/V4__add_user_deactivation_fields.sql` | Create |
| 2 | `src/main/java/.../domain/User.java` | Modify |
| 4 | `src/main/java/.../repository/UserRepository.java` | Modify |
| 5 | `src/main/java/.../repository/UserRoleRepository.java` | Modify |
| 6 | `src/main/java/.../service/exception/UserDeactivatedException.java` | Create |
| 7 | `src/main/java/.../service/UserSyncService.java` | Modify |
| 8 | `src/main/resources/application.yml` | Modify |
| 9 | `src/main/java/.../config/PermissionServiceConfig.java` | Modify |
| 10 | `src/main/java/.../client/SessionGatewayClient.java` | Create |
| 11 | `src/main/java/.../api/request/UserDeactivationRequest.java` | Create |
| 12 | `src/main/java/.../api/response/UserDeactivationResponse.java` | Create |
| 13 | `src/main/java/.../service/dto/UserDeactivationResult.java` | Create |
| 14 | `src/main/java/.../service/UserService.java` | Modify |
| 15 | `src/main/java/.../api/InternalPermissionController.java` | Modify |
| 16 | `src/main/java/.../config/PermissionServiceSecurityConfig.java` | Modify |
| 17 | `src/test/java/.../TestConstants.java` | Modify |
| 18 | `src/test/java/.../service/UserSyncServiceTest.java` | Modify |
| 19 | `src/test/java/.../service/UserServiceTest.java` | Create |
| 20 | `src/test/java/.../client/SessionGatewayClientTest.java` | Create |
| 21 | `src/test/java/.../api/InternalPermissionControllerTest.java` | Modify |

**Total**: 9 new files, 12 modified files.

---

## Verification

1. `./gradlew clean build` — compiles, all tests pass
2. `./gradlew clean spotlessApply` — code formatted
3. Manual: start service with PostgreSQL, verify V4/V5 migrations run (`\d users` shows the new
   columns and `idx_users_idp_sub_status`)
4. Manual: call `POST /internal/v1/users/{userId}/deactivate` with a test user
5. Manual: call `GET /internal/v1/users/{idpSub}/permissions` for a deactivated user — verify 422
6. Manual: call deactivation again for same user — verify idempotent 200

---

## Documentation Updates

After implementation:
- Update `AGENTS.md` — add `UserDeactivatedException` to exception section, note the deactivation
  endpoint under "Internal Permissions Endpoint", add Session Gateway client to discovery commands
- Update `docs/` if any architecture docs exist for this service
