# Security Consensus Hardening Evidence

## 2026-11-07: Safe logging, readiness, audit and authorization coverage

Default and `kubernetes` configuration log `org.budgetanalyzer` at `INFO`. HTTP request and
response body flags are absent from the base configuration, so service-common's safe `false`
defaults apply. Body capture and application `TRACE` logging are available only through the
opt-in `debug-logging` profile, which is documented for synthetic local data only.

The dedicated `budgetanalyzer.audit` logger is explicitly enabled at `INFO`, independently of the
root `WARN` threshold. A full-context request test verifies that one authenticated user
deactivation emits exactly one audit event with the route pattern and actor identifier.

### Readiness and authorization coverage

Health probes are enabled outside Kubernetes as well as in-cluster. Liveness retains only process
state. Readiness includes `readinessState` and PostgreSQL's `db` indicator. Session Gateway is
excluded because permission request serving does not depend on it.

A full-context mapping test enumerates application handlers from `RequestMappingHandlerMapping`
and rejects every handler lacking method- or class-level `@PreAuthorize`. The explicit exceptions
are the POST user-sync and GET authorization handlers in `InternalPermissionController`; their
method-and-path-scoped protection remains in `PermissionServiceSecurityConfig`.

### Validation

- `./gradlew test --tests
  'org.budgetanalyzer.permission.PermissionServiceApplicationIntegrationTest' --tests
  'org.budgetanalyzer.permission.api.RuntimeSecurityIntegrationTest'` passed all four focused
  tests. They confirmed safe effective `kubernetes` logging, database-sensitive readiness,
  dependency-free liveness, complete handler authorization declarations and exactly one audit
  event for a successful authenticated deactivation.
- `./gradlew clean spotlessApply` passed.
- `./gradlew clean build` passed all 63 tests with no failures or skips and no Checkstyle
  findings. JaCoCo covered 364 of 424 lines and 61 of 82 branches, above the configured gates.
- `git diff --check` passed.

## 2026-11-07: Unused user deletion path removal

A repository search found `deleteUser` only at its declaration in `UserService`;
there was no controller, production caller, or deletion-only test. The method was
removed without adding a deletion endpoint or changing account-lifecycle policy.
Deactivation remains the supported operation and continues to remove roles,
commit the durable account block, and revoke sessions.

The synchronization guard remains unchanged: it checks for any matching
`DEACTIVATED` row regardless of soft-delete state. Existing integration coverage
continues to verify that a deactivated identity cannot synchronize again, while
a soft-deleted `ACTIVE` identity may be recreated.

### Validation

- `./gradlew test --tests
  'org.budgetanalyzer.permission.service.UserServiceIntegrationTest' --tests
  'org.budgetanalyzer.permission.service.UserSyncServiceIntegrationTest'`
  passed.
- `./gradlew clean spotlessApply` passed.
- `./gradlew clean build` passed all compilation, test, Checkstyle, Spotless,
  JaCoCo coverage-verification, and packaging tasks without findings.
- `git diff --check` passed.
