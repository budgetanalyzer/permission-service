# permission-service Plan: Permission-Based Authorization Cleanup

Date: 2026-04-08

Status: Draft

## Context

This is the permission-service slice of the cross-service plan
`/workspace/architecture-conversations/docs/plans/permission-based-authorization-cleanup.md`.
Read that document first for the full architectural picture and the rationale for
`:any` scope naming.

Scope of this plan: everything that lives under `/workspace/permission-service/`. Changes to
`transaction-service`, `session-gateway`, `budget-analyzer-web`, and `orchestration` are
tracked by the cross-service plan and are explicitly **not** in scope here.

## Goal

Add three cross-user transaction permissions to the seed data so that other services can
replace their remaining `hasRole('ADMIN')` checks with `hasAuthority('transactions:*:any')`:

- `transactions:read:any`
- `transactions:write:any`
- `transactions:delete:any`

The new permissions are granted to `ADMIN` only. `USER` does **not** receive them.

## Current State

- Permission count after V2 + V3: **21** seeded permissions, ADMIN bundles all 21.
- Last applied migration: `V4__add_user_deactivation_fields.sql` (schema-only, no permission
  rows).
- Doc drift already present:
  - `README.md:107` says "All 16 permissions" (stale since V3).
  - `AGENTS.md:84` says "All 19 permissions" (also stale — the real count after V3 is 21).
  This plan corrects both to 24 as part of the same change.

## Target State

- Permission count after V5: **24** seeded permissions, ADMIN bundles all 24, USER unchanged
  at 6.
- `:any` scope convention is documented in `AGENTS.md` as the pattern for future scoped
  permissions.

## Changes

### 1. New Flyway Migration

**File:** `src/main/resources/db/migration/V5__add_cross_user_transaction_permissions.sql`
(new)

Must match the style of `V3__add_currency_and_statementformat_permissions.sql`:
plain `INSERT INTO ... VALUES`, audit columns populated (`created_at`, `created_by='SYSTEM'`),
banner comments grouping the permission inserts and the role grants.

```sql
-- =============================================================================
-- Cross-user transaction permissions (":any" scope)
-- Enables transaction-service to replace hasRole('ADMIN') with proper
-- permission-based authorization for cross-user code paths.
-- See: architecture-conversations/docs/plans/permission-based-authorization-cleanup.md
-- =============================================================================
INSERT INTO permissions (id, name, resource_type, action, created_at, created_by) VALUES
    ('transactions:read:any',   'Read Any User''s Transactions',   'transaction', 'read',   CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:write:any',  'Write Any User''s Transactions',  'transaction', 'write',  CURRENT_TIMESTAMP, 'SYSTEM'),
    ('transactions:delete:any', 'Delete Any User''s Transactions', 'transaction', 'delete', CURRENT_TIMESTAMP, 'SYSTEM');

-- =============================================================================
-- ADMIN gets all three; USER gets none (cross-user scope is admin-only)
-- =============================================================================
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by) VALUES
    ('ADMIN', 'transactions:read:any',   CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'transactions:write:any',  CURRENT_TIMESTAMP, 'SYSTEM'),
    ('ADMIN', 'transactions:delete:any', CURRENT_TIMESTAMP, 'SYSTEM');
```

Notes on the SQL above:

- `resource_type` stays `transaction` (the existing `:read`/`:write`/`:delete` rows use the
  singular form — check V2 and match).
- `action` is the base action (`read`/`write`/`delete`) — the scope lives in the `id`/`name`,
  not in the `action` column. This matches the plan's naming convention
  `{resource}:{action}:{scope}` without requiring a schema change.
- Flyway checksumming handles re-runs; no `ON CONFLICT` needed (V2/V3 don't use one either).
- If an operator re-points at a fresh DB, V2 + V3 + V5 reproduce the full seed state.

### 2. Tests

The existing controller tests (`InternalPermissionControllerTest`,
`UserPermissionControllerTest`) use Mockito-stubbed `EffectivePermissions` objects and do
**not** hard-code the real seed count, so no fixture updates are required there. A grep for
the literals `16`/`21` across `src/test/` returns no matches — confirmed.

What to add:

**File:** new test class
`src/test/java/org/budgetanalyzer/permission/repository/SeedDataIntegrationTest.java`

Purpose: assert that the Flyway-seeded data has the expected shape after V5. This is the
first test in this repo that validates migration output, so it sets a small precedent —
worth it because the plan's correctness depends on exactly which rows land in the DB.

Use `@DataJpaTest` + `PostgreSQLContainer` (see
`UserRoleRepositoryIntegrationTest` for the existing TestContainers pattern) and the real
`PermissionRepository` + `RolePermissionRepository` beans. Assertions:

1. Count of `permissions` rows equals **24**.
2. `permissions` contains the three new IDs:
   - `transactions:read:any`
   - `transactions:write:any`
   - `transactions:delete:any`
3. `role_permissions` for `role_id='ADMIN'` contains the three new IDs.
4. `role_permissions` for `role_id='USER'` does **not** contain any of the three.
5. ADMIN role's total `role_permissions` count equals 24.
6. USER role's total `role_permissions` count equals 6 (unchanged).

If `PermissionRepository`/`RolePermissionRepository` do not yet exist or do not expose the
needed finders, add minimal query methods in the same change — do not create a new
repository class just for the test.

**File to inspect:** `src/test/java/org/budgetanalyzer/permission/repository/UserRoleRepositoryIntegrationTest.java`
— copy its TestContainers bootstrap (datasource, Flyway config) verbatim.

### 3. Documentation Updates

All documentation updates land in the same commit as the migration. Do not split.

**`README.md`**

- Line 107: change `All 16 permissions` → `All 24 permissions`.
- Under "Default Roles" or a new "Permissions" subsection, add a brief note explaining the
  `:any` scope convention and listing the three new IDs. Keep it short — the authoritative
  description lives in the architecture-conversations plan.

**`AGENTS.md`**

- Line 84: change `All 19 permissions` → `All 24 permissions`. (Note the existing `19` is
  already stale — V3 brought the real count to 21; V5 brings it to 24. Fix both in one edit.)
- Add a short block under "Service-Specific Patterns" documenting the scope convention:

  > **Scoped permissions.** The base `{resource}:{action}` pattern is extended to
  > `{resource}:{action}:{scope}` where the scope is omitted for the default (own-resources)
  > case and `:any` denotes cross-user access. Established by V5 for transactions; future
  > scoped permissions should follow the same pattern. Add scoped variants only when a
  > controller actually needs cross-user code paths — do not pre-create them.

- The existing "Adding new permissions" note at line 222-223 already tells agents to update
  `ClaimsHeaderTestBuilder` in `service-common` when adding permissions. That guidance stays
  as-is. See §4 below for the cross-repo follow-up.

### 4. Cross-Repo Dependency (Outside This Repo's Write Boundary)

`../service-common/service-web/src/main/java/org/budgetanalyzer/service/security/test/ClaimsHeaderTestBuilder.java:62-84`
hard-codes the full `ADMIN_PERMISSIONS` list. When V5 lands, tests across **every** service
that use `ClaimsHeaderTestBuilder.admin()` will build an admin principal that lacks the three
new `:any` permissions — which is fine for tests that don't exercise `:any` code paths, but
will break `transaction-service`'s new authorization tests in the cross-service plan.

**This repo cannot edit `service-common`.** The required changes there are:

1. Append the three new strings to `ADMIN_PERMISSIONS`.
2. Optionally update `ClaimsHeaderTestBuilderTest` if it asserts a specific list length.

This must land in `service-common` **before or alongside** the transaction-service work
(step 2 of the cross-service plan's sequencing). Flagging it here so it is not missed. Do not
open a PR against `service-common` from this repo; route the change through whatever process
owns that repository.

The V5 migration itself does not depend on `service-common` being updated — the migration
can ship independently. The dependency is only on the downstream test updates.

### 5. Verification

After applying the change, run locally:

```bash
./gradlew clean spotlessApply
./gradlew clean build
```

The full build exercises:

- Flyway migration parse/apply against the TestContainers PostgreSQL.
- The new `SeedDataIntegrationTest`.
- All existing controller and service tests (unchanged — Mockito-stubbed).

Manual sanity check (optional, requires local PostgreSQL):

```bash
./gradlew bootRun
# In another shell:
psql -h localhost -U permission -d permission -c \
  "SELECT id FROM permissions WHERE id LIKE 'transactions:%:any' ORDER BY id;"
# Expected: three rows.

psql -h localhost -U permission -d permission -c \
  "SELECT COUNT(*) FROM role_permissions WHERE role_id='ADMIN';"
# Expected: 24.
```

## Sequencing

This plan is step 1 of the cross-service plan's sequencing section. It unblocks
transaction-service and session-gateway (step 2) but does not itself depend on any other
service change.

Within this plan, the order is:

1. Write `V5__add_cross_user_transaction_permissions.sql`.
2. Write `SeedDataIntegrationTest` and get it green.
3. Update `README.md` and `AGENTS.md`.
4. Run `./gradlew clean spotlessApply && ./gradlew clean build`.
5. Commit (subject to user approval — no autonomous git operations).

All four steps land together in a single commit. The migration and the test that verifies
the migration belong in the same change.

## Out of Scope

Explicitly **not** part of this plan:

- Any controller changes in permission-service. No `@PreAuthorize` annotations need to
  change; the two existing controllers (`InternalPermissionController`,
  `UserPermissionController`) are not affected by the `:any` permissions.
- Any API response changes. The `EffectivePermissions` DTO already returns whatever
  permissions the DB has; the new rows flow through automatically.
- Updates to `ClaimsHeaderTestBuilder` (see §4 — different repo).
- Adding `:any` variants for any other resource (accounts, budgets, currencies,
  statementformats, users, roles). The cross-service plan is explicit that scoped variants
  are added only when a real code path needs them.
- Renaming or removing the `ADMIN` role.
- Any audit-logging work around `:any` permission usage.

## Open Questions

None at this time. The SQL shape, test location, and doc updates are all unambiguous given
the cross-service plan and the existing V3 migration as a template. If the reviewer wants a
different assertion style in `SeedDataIntegrationTest` (e.g., snapshot-based rather than
explicit count) flag during review.
