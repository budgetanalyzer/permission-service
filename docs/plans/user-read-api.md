# User Read API for Permission Service

## Context

The permission-service currently only exposes a single admin action on users: `POST /v1/users/{id}/deactivate` on `UserPermissionController`. There is no way for an admin UI to browse or inspect users, which blocks building a user-management screen on top of the service.

We need read-only endpoints that:
1. List users with pagination, filtering, and sorting — following the proven pattern from `transaction-service` `searchTransactions`.
2. Return a single user's details.
3. Include each user's role assignments in the response, since role membership is the main thing an admin cares about when looking at a user.

As part of the change, `UserPermissionController` is being renamed to `UserController` — the old name was a misnomer (it never managed permissions) and the class is growing beyond deactivation.

The `users:read` permission already exists and is seeded to `ADMIN` (`V2__seed_default_data.sql:27`, `:84`) and `TestConstants.PERM_USERS_READ` is already defined — no migration or test-constant work needed.

## Reference pattern

`transaction-service` search, to be mirrored:
- `transaction-service/src/main/java/org/budgetanalyzer/transaction/api/TransactionController.java:254-305` — `searchTransactions`, `validateSortFields`, `ALLOWED_SORT_FIELDS`, `@PageableDefault`, `@ParameterObject`.
- `transaction-service/src/main/java/org/budgetanalyzer/transaction/api/request/TransactionFilter.java` — filter record with `@Schema` annotations and `empty()` factory.
- `transaction-service/src/main/java/org/budgetanalyzer/transaction/repository/spec/TransactionSpecifications.java` — `Specification` builder using JPA Criteria.
- `transaction-service/src/main/java/org/budgetanalyzer/transaction/service/TransactionService.java:200-203` — service delegates to `repository.findAllNotDeleted(spec, pageable)` from `SoftDeleteOperations`.
- `service-common/service-web/src/main/java/org/budgetanalyzer/service/api/PagedResponse.java` — response wrapper.

`UserRepository` already extends `SoftDeleteOperations<User, String>` which extends `JpaSpecificationExecutor<User>`, so `findAllNotDeleted(spec, pageable)` is available today with zero plumbing.

## Roles-per-user strategy

Two-query batch (clean, no N+1, no domain changes):

1. `userRepository.findAllNotDeleted(spec, pageable)` → `Page<User>`.
2. Collect page user IDs → `userRoleRepository.findByUserIdIn(userIds)` (new method) → `List<UserRole>`.
3. Group into `Map<String, List<String>> rolesByUserId` via `Collectors.groupingBy`.
4. Zip into `UserSummaryResponse` records.

Rejected alternatives:
- JOIN FETCH / `@OneToMany` on `User` — `UserRole` is intentionally a bare join table (see `UserRole.java:14-19`); adding a collection to `User` expands the domain model for a read-only concern.
- Native aggregate query — fights Spring Data `Specification` + `Pageable` machinery.
- Per-user lookup inside a loop — classic N+1.

## Endpoints

### `GET /v1/users` — search users
- `@PreAuthorize("hasAuthority('users:read')")`
- Query params bound via `@ParameterObject UserFilter` + `@ParameterObject Pageable`
- `@PageableDefault(size = 50, sort = {"createdAt", "id"}, direction = Sort.Direction.DESC)`
- Returns `PagedResponse<UserSummaryResponse>`
- Validates sort fields against `ALLOWED_SORT_FIELDS` (throws `InvalidRequestException` on unknown — same as `TransactionController.validateSortFields`)

### `GET /v1/users/{id}` — user details
- `@PreAuthorize("hasAuthority('users:read')")`
- Returns `UserDetailResponse` (404 via `ResourceNotFoundException` from `UserService.getUser`)

## Files to create

### `api/request/UserFilter.java`
Record with `@Schema` annotations, matching `TransactionFilter` style. Fields:
- `String id`
- `String email` (LIKE, multi-word OR like `TransactionSpecifications.createTextFilterPredicate`)
- `String displayName` (LIKE, multi-word OR)
- `String idpSub` (exact match)
- `UserStatus status` (enum exact match)
- `Instant createdAfter`, `Instant createdBefore`
- `Instant updatedAfter`, `Instant updatedBefore`
- `static UserFilter empty()`

### `repository/spec/UserSpecifications.java`
- `withFilter(UserFilter)` — mirrors `TransactionSpecifications.withFilter`, reusing the same `createTextFilterPredicate` + `escapeLikePattern` helper logic (copy locally; no shared util exists in `service-common`, and cross-service util extraction is out of scope).

### `api/response/UserSummaryResponse.java`
Record returned in list view:
```
String id, String idpSub, String email, String displayName,
UserStatus status, List<String> roleIds,
Instant createdAt, Instant updatedAt, Instant deactivatedAt
```
- Static `from(User, List<String> roleIds)` factory.

### `api/response/UserDetailResponse.java`
Record returned by detail endpoint. Same shape as `UserSummaryResponse` plus `deactivatedBy` and `deletedAt`/`deletedBy` (they live on `SoftDeletableEntity` and are useful for admin forensics).
- Static `from(User, List<String> roleIds)` factory.

### `service/dto/UserSearchResult.java` (optional)
Only if the `(Page<User>, Map<String,List<String>>)` tuple leaks awkwardly; likely inlined in the service.

## Files to modify

### `api/UserPermissionController.java` → `api/UserController.java`
- Rename class + file. Keep `@RequestMapping("/v1/users")`.
- Add `getUsers(UserFilter, Pageable)` and `getUser(String id)` methods.
- Add `ALLOWED_SORT_FIELDS` constant: `id, email, displayName, status, createdAt, updatedAt, deactivatedAt`.
- Add `validateSortFields` helper (copy pattern from `TransactionController:489-499`).
- Inject the existing `UserService` (already injected) — no new constructor deps needed on the controller.

### `service/UserService.java`
- Add `Page<UserWithRoles> search(UserFilter filter, Pageable pageable)` that runs the two-query batch and returns a page of a small record `UserWithRoles(User user, List<String> roleIds)`. Keeps the controller free of JPA plumbing and keeps the mapping (User → response) in the controller layer.
- Add `UserWithRoles getUserWithRoles(String id)` that calls existing `getUser(id)` + `userRoleRepository.findRoleIdsByUserId(id)`.
- The existing `getUser(String id)` stays as-is (still used by `deactivateUser` path).

### `repository/UserRoleRepository.java`
- Add `List<UserRole> findByUserIdIn(Collection<String> userIds)` — Spring Data derived query, no custom JPQL needed.

### `test/java/.../api/UserPermissionControllerTest.java` → `UserControllerTest.java`
- Rename class + file to match.
- Keep existing `DeactivateUserTests` nested class as-is.
- Add `GetUsersTests` nested class:
  - happy path (filter + sort + page) → 200 with `PagedResponse` body
  - invalid sort field → 400
  - missing `users:read` → 403
- Add `GetUserTests` nested class:
  - happy path → 200 with roles
  - not found → 404
  - missing `users:read` → 403

### `src/main/resources/static/openapi.yaml` or OpenAPI regeneration
- No manual edit; springdoc regenerates from annotations.

### `README.md`
- Add new rows to the endpoint table (`README.md:137` area):
  - `GET /v1/users` — Search users — `users:read`
  - `GET /v1/users/{id}` — Get user by ID — `users:read`

### `AGENTS.md`
- Update `AGENTS.md:150` reference from `UserPermissionController` to `UserController` and note the new endpoints.

## Non-goals / explicit exclusions

- No new DB migration (uses existing `users` + `user_roles` tables).
- No new permission (`users:read` already exists and is seeded).
- No role name/description lookup — role IDs like `ADMIN` / `USER` are already human-readable; expanding to full `Role` objects can be a follow-up if the UI needs it.
- No filtering by `roleId` on the list endpoint (can be added later via a subquery spec if needed).
- No inclusion of soft-deleted users (`findAllNotDeleted` excludes them). Deactivated users are included since `deactivated` is a status, not a soft-delete.

## Verification

1. `./gradlew compileJava` — ensure rename doesn't break references.
2. `./gradlew test --tests "*UserControllerTest*"` — new controller tests pass.
3. `./gradlew test` — full suite stays green (catches any references to the old class name we missed).
4. `./gradlew bootRun` and hit:
   - `GET /permission-service/v1/users?size=5&sort=createdAt,desc` with `users:read`
   - `GET /permission-service/v1/users?email=admin&status=ACTIVE`
   - `GET /permission-service/v1/users?sort=bogus` → expect 400
   - `GET /permission-service/v1/users/{id}`
   - Same calls without the permission → expect 403.
5. Swagger UI at `/permission-service/swagger-ui.html` shows both new endpoints under the renamed tag with `UserFilter` params documented.
