# Authorization Model

Reference for how roles and permissions work in this service, why the model is shaped the way it is, and what UI layers should do with it. Written to be re-readable months later without rebuilding context.

## Why this document exists

This service owns RBAC data (roles, permissions, user-role assignments) and exposes an internal endpoint that downstream services call to resolve a user's effective permissions. The question that motivated writing this down is a UI question: **when building UI controls (route guards, buttons, menu items) against this data, should the frontend key off roles or off permissions?** The answer is non-obvious because the current seeded data makes role checks and permission checks almost equivalent, which is misleading. This doc captures the decision and the reasoning so the equivalence doesn't cause drift later.

## Data model

Five tables, no magic:

```
users ──< user_roles >── roles ──< role_permissions >── permissions
```

- `roles` (`domain/Role.java`): id, name, description, `is_system` flag. The flag is informational — it is **not** enforced by the resolver.
- `permissions` (`domain/Permission.java`): atomic `resource:action` strings (e.g. `transactions:read`). Permissions are the unit of authority checked by downstream services.
- `role_permissions` (`domain/RolePermission.java`): join rows. A role is defined by its set of join rows — nothing else.
- `user_roles` (`domain/UserRole.java`): join rows. `UNIQUE(user_id, role_id)` only — a user can hold multiple roles.
- `users` (`domain/User.java`): the subject. No direct permission columns.

Resolution happens in one query, `UserRoleRepository.findPermissionIdsByUserId`:

```sql
SELECT rp.permissionId FROM UserRole ur
JOIN RolePermission rp ON ur.roleId = rp.roleId
WHERE ur.userId = :userId
```

`PermissionService.getEffectivePermissions` returns a flat `EffectivePermissions(Set<String> roles, Set<String> permissions)` DTO containing both the user's role ids and the union of permission ids across those roles. There is no `if (role == ADMIN)` branch anywhere in the service.

## The ADMIN seed bundle convention

ADMIN's broad access comes from explicit seed rows, not from any special-case code:

```sql
-- V2__seed_default_data.sql
INSERT INTO role_permissions (role_id, permission_id, ...) VALUES
  ('ADMIN', 'transactions:read', ...),
  ...
```

Today that bundle intentionally excludes all `views:*` permissions and also excludes
`statementformats:delete`. Those own-resource saved-view permissions stay USER-only until there
is a real cross-user workflow that justifies scoped variants such as `views:read:any`, and
statement format management is intentionally limited to read/write. **This is a seed-data
convention, not an enforced invariant.** Any new permission or bundle change must be reflected
in `V2__seed_default_data.sql`.

## Invariants to preserve

1. **Role is the bundle.** Permissions are attached to roles, never to users. Two users with the same set of roles must resolve to the same effective permissions.
2. **Permission ids are the authority unit.** Downstream services check `hasPermission('transactions:delete')`, not `hasRole('ADMIN')`. This keeps servers from having to know the role→permission policy.
3. **Users can hold multiple roles.** Effective permissions are the union across roles. No code should assume a single role per user.
4. **No per-user permission overrides today.** The model supports only role-level grants. If exceptions are ever needed, see "Design space for future growth" below.
5. **Action hierarchy at grant time.** For any resource, both `:write` and `:delete` imply `:read`. Every role that holds `{resource}:write` must also hold `{resource}:read`, and every role that holds `{resource}:delete` must also hold `{resource}:read`. `:write` and `:delete` are independent of each other — a role may hold either, both, or neither. See "Permission action hierarchy" below.

## Permission action hierarchy

Permission ids are atomic strings in the database — there is no structural relationship between `currencies:read` and `currencies:write`. But semantically, both modifying actions require the ability to see the resource: you cannot meaningfully edit a resource you cannot read, and you cannot meaningfully identify a resource to delete one you cannot read. Rather than encode that relationship in code (a `hasEffectivePermission()` wrapper, an expansion helper, or a runtime check that expands `:write` into `{:read, :write}`), this service treats it as a **grant-time invariant** enforced by convention at the single place grants happen:

```
    write    delete         (for the same resource)
       \    /
        read

    write:any    delete:any  (for the same scope)
          \    /
         read:any
```

`:write` and `:delete` are **independent**. Legitimate shapes include `{read}`, `{read, write}`, `{read, delete}`, and `{read, write, delete}`. A "can update but not destroy" editor role and a "can archive but not modify" purger role are both expressible. The only forbidden shapes are the ones that hold a modifying action without `:read`: `{write}` alone, `{delete}` alone, and `{write, delete}` without `read`.

### Why encode the hierarchy at grant time instead of at check time

Every runtime approach adds code for no payoff. An `includes()` check against the effective-permissions set is already correct if the set is well-formed at issue time — and the set is well-formed because it is assembled from `role_permissions` rows that this service writes via migrations. There is no other grant surface. A runtime expansion helper would be dead code: it would only ever fire on permission sets that the service itself refuses to produce.

The upside is concrete: downstream callers (Session Gateway, UI route guards, controller `@PreAuthorize` annotations) can do literal string checks. A React route guard that requires `currencies:write` to mount an edit page does not also need to check `currencies:read`, because any role holding `currencies:write` holds `currencies:read` by construction. The button component requiring `currencies:write` mirrors the server's own `@PreAuthorize("hasAuthority('currencies:write')")` check exactly. No client-side policy duplication.

### Where the invariant is enforced

The **single enforcement point** is Flyway migrations under `src/main/resources/db/migration/`. Specifically:

- `V2__seed_default_data.sql` — the initial ADMIN and USER grants. Both bundles respect the hierarchy today: every `:write` and every `:delete` grant is accompanied by `:read`, including the `:any` scoped variants. Some resources in the current bundles happen to hold `{read, write, delete}` together, but that is a property of those specific grants, not a requirement of the invariant.
- Any future migration that inserts into `role_permissions` — the author must grant `{resource}:read` alongside any `{resource}:write` or `{resource}:delete` grant. This includes scoped variants: `{resource}:write:any` and `{resource}:delete:any` each require `{resource}:read:any` on the same role.
- Any future migration that deletes from `role_permissions` — the author must revoke `{resource}:write` and `{resource}:delete` before or at the same time as removing `{resource}:read`, so the role never transiently holds a modifying action without read.

There is no admin UI for editing grants and no runtime grant surface, so the migration convention is sufficient. If a grant UI is ever added, it must either bundle the lower tiers automatically (selecting `:write` auto-selects `:read`) or refuse to save a violating set.

### What the invariant does not cover

- **`:write` and `:delete` are independent.** Holding `currencies:write` says nothing about `currencies:delete`, and vice versa. Each is a separate authority unit that independently requires `:read`.
- **Different resources are independent.** Holding `currencies:write` says nothing about `transactions:read`.
- **Scoped and unscoped are independent.** Holding `transactions:write:any` does not imply `transactions:write` — those are separate authority units. The invariant runs within a scope, not across scopes. Concretely: `transactions:write:any` implies `transactions:read:any`, and `transactions:write` implies `transactions:read`, but neither implies the other.
- **Own-resource `views:*` is a self-contained bundle.** The three view permissions follow the invariant among themselves (USER holds all three today). There are no scoped `views:*:any` variants yet; when they are added they must follow the same internal rule.

### Drift risk and the optional safety net

"Enforced by convention in migrations" is not a database constraint — a future migration could violate it, or a hand-edited row could. Two cheap safety nets exist if drift becomes a real concern:

- **Migration-author checklist.** Keep the invariant prominent in `AGENTS.md`, this document, and in a comment block above the `role_permissions` grants in `V2__seed_default_data.sql` so the next migration author sees it at the grant surface before writing rows. This is the current approach. Any future migration that touches `role_permissions` should repeat the rule in its own header comment.
- **Test-time assertion.** `SeedDataIntegrationTest` already loads the full seed set per role. A one-method assertion that walks each role's permissions and checks "for every `{r}:write` the role holds `{r}:read`, and for every `{r}:delete` the role holds `{r}:read`" would catch any violating migration in CI without adding runtime code. Optional — add it only if a real violation is ever observed or a grant UI is introduced.

Runtime expansion (an `effective()` helper that returns the downward closure of a permission set) is explicitly **not** the answer. It pushes policy into code in exchange for tolerating malformed grant rows the service itself never writes.

## UI authorization decisions

The guidance is borrowed from [bulletproof-react's authorization pattern](https://github.com/alan2207/bulletproof-react/blob/master/apps/react-vite/src/lib/authorization.tsx): **roles for layouts, permissions for actions.**

| UI concern | Check | Rationale |
|---|---|---|
| Route guards, admin sidebars, top-level navigation | Role (`hasRole('ADMIN')`) | Answers "who is this person structurally?" Layouts change rarely and are few. A role check reads naturally. |
| Buttons, menu items, enable/disable of action controls | Permission (`hasPermission('transactions:delete')`) | Answers "will the server accept this action?" The button is mirroring the server's own check. Self-documenting at the call site. |

Both checks read from the same `EffectivePermissions` payload — the service already returns `roles` and `permissions` together, so there is no extra round trip to pay.

### Why not use role checks for actions, given that roles == permission sets today?

The equivalence is coincidental, not structural, and will break silently the first time any of these happens:

- A third role is added that overlaps with ADMIN on some but not all permissions. Any UI that wrote `hasRole('ADMIN')` to mean "can do X" is now wrong.
- A user is assigned multiple roles. `hasRole` has to answer "which one?" and couples the UI to the role composition logic.
- An existing role gains a new permission in a later migration. Every `role === 'USER'` check in the UI that secretly meant "can do X" is now wrong with no compile error or test failure.

Keying the UI off permissions means the frontend mirrors the server's invariant (`transactions:delete` → can delete transactions) instead of duplicating the policy (`ADMIN` → can delete transactions because of seed data). The permission check is the more durable abstraction at essentially zero extra cost.

### Anti-patterns to avoid in the UI

- **Do not key action buttons off role names.** `if (role === 'ADMIN')` at a button site hides the policy in the component instead of expressing it at the call.
- **Do not invent UI-side role→permission lookup tables.** The server already resolves this. Duplicating it in the frontend means two places to update.
- **Do not assume a single role per user.** Treat roles as a `Set`.

## Design space for future growth

If the "limited admin" or "custom permission set per user" requirement ever surfaces, these are the documented options in order of disruption:

1. **Narrow roles (composition).** Define `BUDGET_ADMIN`, `USER_ADMIN`, `AUDIT_READER`, etc., and assign combinations. Works today with zero schema change. Risk: role explosion if dimensions multiply.
2. **Additive user-level grants (delta table).** Add a `user_permissions` table that is purely additive — "Alice gets `audit:read` on top of her USER role." Resolution becomes `UNION` of role-derived and user-derived sets. ~20 line change in `UserRoleRepository`. Keeps role-as-bundle invariant intact.
3. **Scoped RBAC (RBAC + ABAC).** Keep coarse roles and add a scope attribute ("admin over tenant X", "admin over owned accounts"). Requires every downstream service to apply the scope when filtering queries. Matches the pattern used by AWS IAM, GCP IAM, Azure RBAC.
4. **External policy engine (Cerbos / OPA / Oso / SpiceDB).** Move authorization decisions out of tables and into a rules engine. Warranted only at much larger scale.

### Deferred: grant/revocation audit trail

`UserRole` and `RolePermission` currently extend `AuditableEntity`, which means `createdAt`/`createdBy` capture when a grant was made but nothing captures when one is revoked — `repository.delete(...)` removes the row entirely. This is acceptable today because there is no revocation flow: no controller, no service method, no admin UI deletes these rows. Seed data in `V2__seed_default_data.sql` is the only writer.

When a revocation flow is added, choose between:

1. **Upgrade the join entities to `SoftDeletableEntity`.** Matches the pattern used by `User`, `Role`, and `Permission`. Cheapest option. Forces a decision on re-grant semantics: is re-granting a previously-revoked role a `restore()` of the old row, or a new row with a new `createdAt`? Both are defensible; pick one and document it.
2. **Introduce a `permission_audit_log` table.** Append-only event table with `actor`, `action` (`GRANT` | `REVOKE`), `user_id`, `role_id` or `permission_id`, `timestamp`, `reason`. Join tables stay lean; all grant/revoke history becomes first-class records. Preferred if compliance questions ever enter the picture.

Do not make this decision preemptively. Make it at the same time as the revocation flow — the flow's requirements (undo? audit queries? time-travel reporting?) will determine which option fits.

**Why join tables are not soft-deletable today:** the row's existence *is* the business fact ("this user has this role"). With no revocation flow and no audit requirement, there is nothing for a `deleted=true` row to accomplish that an absent row does not already. Adding soft-delete columns now would be infrastructure without a consumer. See `service-common/docs/spring-boot-conventions.md` → Base Entity Classes → Choosing Between Them for the general rule.

### Explicitly rejected: snapshot-on-user-creation

An earlier idea was to snapshot a role's permissions into a `user_permissions` table at user creation time, then let operators refine that snapshot. **This is not the chosen approach.** Failure modes:

- **Permission drift.** New permissions added in later migrations would never reach existing users, and there would be no way to distinguish "intentionally customized" snapshots from "just stale" ones.
- **Role names become meaningless.** If Alice and Bob both hold `ADMIN` but have different effective permissions, audit questions like "who can delete users?" can no longer be answered by querying `role_permissions`.
- **Source-of-truth ambiguity.** No clean answer to "is the snapshot the truth, or is it role + overrides?" Every operation has edge cases (what if the role later drops a permission the user snapshotted?).

If per-user exceptions are ever needed, use option 2 (additive delta) instead. It preserves role-as-bundle and avoids drift.

## Quick reference

- **Where permissions are defined:** `db/migration/V*.sql`
- **Where ADMIN's 13-permission non-view bundle is defined:** `db/migration/V2__seed_default_data.sql`
- **Resolver query:** `UserRoleRepository.findPermissionIdsByUserId`
- **DTO returned to downstream services:** `service/dto/EffectivePermissions.java` (fields: `roles`, `permissions`)
- **Admin user detail response shape:** `api/response/UserDetailResponse.java` (`deactivatedBy` and `deletedBy` are nullable `UserReference { id, displayName, email }` objects)
- **UI rule of thumb:** roles for layouts, permissions for actions
- **Action hierarchy rule of thumb:** both `:write` and `:delete` require `:read` on the same resource/scope; `:write` and `:delete` are independent of each other. Downstream checks stay literal.
