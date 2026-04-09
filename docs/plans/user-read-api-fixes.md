# User Read API — Code Review Fixes

## Context

Follow-up to the `get-users-endpoint` branch (commits `d7bfd85..fa58f00`) which added
`GET /v1/users`, `GET /v1/users/{id}`, renamed `UserPermissionController` →
`UserController`, and introduced `UserFilter`, `UserSummaryResponse`,
`UserDetailResponse`, `UserReference`, `UserSpecifications`, `UserDetail`, and
`UserWithRoles`.

A code review against `service-common/docs/code-quality-standards.md` and
`service-common/docs/testing-patterns.md` found violations that were not caught
by the existing convention in the repo (some of which pre-date this branch but
get propagated by the new tests). This plan scopes the cleanup to files the
branch already touches, plus a layering fix that was deferred pending an
investigation of `transaction-service`.

## In-scope fixes

All changes are local, reversible, and do not change production behavior.
Verification for every step: `./gradlew clean spotlessApply && ./gradlew clean build`.

### Step 1 — Drop exception-message assertions (testing-patterns.md violation)

`testing-patterns.md` §"Service Layer Exception Testing" — do not assert on
exception message text. Exception type is part of the contract, message is not.

`src/test/java/org/budgetanalyzer/permission/service/UserServiceTest.java`:

- Line 146: remove `.hasMessageContaining(TestConstants.TEST_USER_ID);`
- Line 168: remove `.hasMessageContaining(TestConstants.TEST_USER_ID);`
- Lines 193-194: remove both `.hasMessageContaining(...)` calls
- Line 223: remove `.hasMessageContaining(TestConstants.TEST_USER_ID);`

Keep `.isInstanceOf(ResourceNotFoundException.class)` /
`.isInstanceOf(ServiceUnavailableException.class)` on each assertion.

These assertions existed on `main` before this branch — the branch preserves
the anti-pattern, it does not introduce it. See follow-up F2 for the broader
cleanup.

### Step 2 — Drop redundant `@DisplayName` from new tests

`testing-patterns.md` §"Test Naming Convention" — use `@DisplayName` only when
the functionality is not obvious from the method name alone.

Remove `@DisplayName` from every `@Test` method in:

- `src/test/java/org/budgetanalyzer/permission/api/UserControllerTest.java`
  (lines 57, 108, 124, 139, 170, 195, 212, 227, 248, 265, 275)
- `src/test/java/org/budgetanalyzer/permission/service/UserServiceTest.java`
  (lines 73, 108, 135, 152, 173-175, 202-205, 234, 278, 296, 322, 345, 386,
  417, 451, 483)

Keep:

- Class-level `@DisplayName("UserController")` / `@DisplayName("UserService")`
  — makes Surefire output readable.
- `@Nested` class-level `@DisplayName("GET /v1/users")`, `@DisplayName("deactivateUser")`,
  etc. — conveys info not present in the inner-class name.

For the two multi-line display names on `UserServiceTest.java:174-175` and
`:202-205`, first rename the method to be fully self-describing, then delete
the annotation:

- `shouldThrowWhenSessionRevocationExhaustsRetries` → keep as-is, it's already clear.
- `shouldThrowWhenSessionRevocationFailsNonRetryable` → keep as-is.

Scope this step to files the branch already touches. Do not touch
`PermissionServiceTest`, `UserSyncServiceTest`, `InternalPermissionControllerTest`,
or the repository integration tests in this PR — see follow-up F1.

### Step 3 — Promote `argThat` to a static import

`src/test/java/org/budgetanalyzer/permission/service/UserServiceTest.java`:

- Add `import static org.mockito.ArgumentMatchers.argThat;` alongside the
  existing `any` / `eq` imports on lines 5-6.
- Line 377: replace `org.mockito.ArgumentMatchers.argThat(` with `argThat(`.

### Step 4 — Use `var` where type is inferrable

`code-quality-standards.md` Rule 1 — use `var` whenever possible for local
variables.

`src/main/java/org/budgetanalyzer/permission/repository/spec/UserSpecifications.java`
lines 90-91:

```java
// Before
String[] words = filterValue.trim().split("\\s+");
List<Predicate> wordPredicates = new ArrayList<>();

// After
var words = filterValue.trim().split("\\s+");
var wordPredicates = new ArrayList<Predicate>();
```

After this change, `java.util.List` is no longer referenced in the file —
remove the `import java.util.List;` on line 4.

### Step 5 — Type-based local variable names

`code-quality-standards.md` Rule 2 — name variables by their full type in camelCase.

- `src/main/java/org/budgetanalyzer/permission/service/UserService.java:192`
  rename `revocationResult` → `sessionRevocationResult` (type is
  `SessionRevocationResult`). Update the reference on line 194.

- `src/main/java/org/budgetanalyzer/permission/api/UserController.java:141`
  rename `result` → `userDeactivationResult` (type is `UserDeactivationResult`).
  Update the references on lines 142-143.

### Step 6 — Lambdas → method references in `UserService.search`

`src/main/java/org/budgetanalyzer/permission/service/UserService.java` lines
101-103, inside the `Collectors.groupingBy` chain:

```java
// Before
Collectors.groupingBy(
    userRole -> userRole.getUserId(),
    Collectors.mapping(
        userRole -> userRole.getRoleId(),

// After
Collectors.groupingBy(
    UserRole::getUserId,
    Collectors.mapping(
        UserRole::getRoleId,
```

Add the `import org.budgetanalyzer.permission.domain.UserRole;` if not already
present. (It is not — `UserRole` is currently only referenced via the implicit
inferred type of the `findByUserIdIn` return.)

### Step 7 — Introduce `service/dto/UserActor` to break the `service → api.response` layering

Status: implemented on 2026-04-09.

**Violation**: `UserService` and `service/dto/UserDetail` both import
`UserReference` from `api/response`, reversing the intended dependency flow
(controller → service → repository).

- `src/main/java/org/budgetanalyzer/permission/service/UserService.java:20`
- `src/main/java/org/budgetanalyzer/permission/service/dto/UserDetail.java:5`

**Fix** — introduce a service-layer DTO and convert at the API boundary:

1. Create `src/main/java/org/budgetanalyzer/permission/service/dto/UserActor.java`:
   ```java
   package org.budgetanalyzer.permission.service.dto;

   import org.budgetanalyzer.permission.domain.User;

   /** Service-layer projection of a user acting as an audit actor. */
   public record UserActor(String id, String displayName, String email) {

     /**
      * Creates an actor from a resolved user.
      *
      * @param user the resolved user
      * @return the user actor
      */
     public static UserActor from(User user) {
       return new UserActor(user.getId(), user.getDisplayName(), user.getEmail());
     }

     /**
      * Creates a degraded actor when only the id is known.
      *
      * @param id the unresolved actor id
      * @return the degraded user actor
      */
     public static UserActor ofIdOnly(String id) {
       return new UserActor(id, null, null);
     }
   }
   ```

2. Change `service/dto/UserDetail.java` to use `UserActor` instead of
   `UserReference`. Drop the `api.response.UserReference` import.

3. Change `UserService.getUserDetail` + `resolveActorReference` to build
   `UserActor` instances. Drop the `api.response.UserReference` import from
   `UserService`.

4. In `api/response/UserReference.java`, add:
   ```java
   /**
    * Creates a reference from a service-layer actor.
    *
    * @param userActor the service-layer actor
    * @return the user reference
    */
   public static UserReference from(UserActor userActor) {
     return new UserReference(userActor.id(), userActor.displayName(), userActor.email());
   }
   ```
   (Or inline the conversion inside `UserDetailResponse.from` — whichever ends
   up cleaner.)

5. In `api/response/UserDetailResponse.from`, map
   `userDetail.deactivatedBy()` / `userDetail.deletedBy()` (now `UserActor`)
   → `UserReference`.

6. Update `UserControllerTest.java` + `UserServiceTest.java` to construct
   `UserActor` instead of `UserReference` where they stub `UserDetail`.

Verification: `grep -r "api\.response" src/main/java/org/budgetanalyzer/permission/service/`
should return zero matches after this step.

## Verification

```bash
cd /workspace/permission-service
./gradlew clean spotlessApply
./gradlew clean build
```

All existing tests must pass. Watch for new Checkstyle warnings in the build
output — none are expected, but treat any warning as a blocker per
`code-quality-standards.md` §"Checkstyle Warning Handling".

## Out of scope — follow-ups

### F1 — Redundant `@DisplayName` cleanup across the repo

`grep -c @DisplayName src/test/java/...` shows 74 uses across 8 files.
Beyond the two files this branch already touches:

- `src/test/java/org/budgetanalyzer/permission/api/InternalPermissionControllerTest.java`
- `src/test/java/org/budgetanalyzer/permission/service/PermissionServiceTest.java`
- `src/test/java/org/budgetanalyzer/permission/service/UserSyncServiceTest.java`
- `src/test/java/org/budgetanalyzer/permission/client/SessionGatewayClientTest.java`
- `src/test/java/org/budgetanalyzer/permission/repository/SeedDataIntegrationTest.java`
- `src/test/java/org/budgetanalyzer/permission/repository/UserRoleRepositoryIntegrationTest.java`

Apply the same pruning rule: drop `@DisplayName` when it duplicates a
camelCase method name; keep class-level and `@Nested` display names. Separate
PR against `main`.

### F2 — Exception-message assertions across the repo

Same methodology as Step 1 for files outside this branch's scope. The
pre-existing `UserServiceTest.java` on `main` already had these assertions,
so they leaked into the new tests when the file was extended. Separate PR
against `main`.

Sweep: `grep -rn "hasMessageContaining" src/test/java/`.

### F3 — Investigate `transaction-service` `service → api` dependencies

Step 7 breaks a layering violation in `permission-service`. The same pattern
exists in `transaction-service` and was the precedent this branch followed
when the review initially flagged it. Before closing F3, understand whether
there is a legitimate reason in `transaction-service` or whether it is
accidental.

Known occurrences as of 2026-04-09 (from
`grep -rn 'import org.budgetanalyzer.transaction.api' src/main/java/org/budgetanalyzer/transaction/service`):

- `service/TransactionService.java` imports `api.request.TransactionFilter`,
  `api.response.PreviewTransaction`
- `service/SavedViewService.java` imports
  `api.request.{CreateSavedViewRequest,UpdateSavedViewRequest,TransactionFilter}`
- `service/StatementFormatService.java` imports
  `api.request.{CreateStatementFormatRequest,UpdateStatementFormatRequest}`
- `service/TransactionImportService.java` imports `api.response.PreviewResponse`
- `service/extractor/StatementExtractor.java` (interface) imports
  `api.response.{PreviewTransaction,PreviewWarning}`
- `service/extractor/CapitalOneBankMonthlyStatementExtractor.java`,
  `CapitalOneCreditMonthlyStatementExtractor.java`,
  `CapitalOneCreditYearlySummaryExtractor.java`,
  `ConfigurableCsvStatementExtractor.java` — all import
  `api.response.PreviewTransaction`
- `repository/spec/TransactionSpecifications.java` imports
  `api.request.TransactionFilter`

Note that `service → api.request` is arguably different from `service →
api.response`: filter objects are cross-cutting query inputs that happen to
be bound to HTTP query strings, and moving them to `service/dto/` would force
the controller to translate (often 1:1) between identical records. The
ecosystem precedent in this repo treats filters as acceptable crossings.
`service → api.response`, by contrast, makes the service layer aware of HTTP
response shape and is the higher-severity direction.

**Goal**: decide, per import, whether to:

1. Move the type to `service/dto/` and have the api layer map it (preferred
   where the type represents a service-layer concept leaking through the
   controller).
2. Accept the crossing and document it as an intentional exception in
   `transaction-service/AGENTS.md` (acceptable where the type is fundamentally
   an HTTP-shaped concept, e.g. `TransactionFilter` bound from query params).

**Deliverable**: either a cleanup PR against `transaction-service`, or a
paragraph in `transaction-service/AGENTS.md` + `service-common/AGENTS.md`
making the precedent explicit so future reviews do not re-flag it.

Start by reading `PreviewTransaction` and `PreviewResponse` in
`transaction-service/src/main/java/org/budgetanalyzer/transaction/api/response/`
and following the call graph from `TransactionImportService` to see whether
the extractor pipeline legitimately needs an HTTP-shaped type or whether it
should work with a service-layer preview type and convert at the controller.
