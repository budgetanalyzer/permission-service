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

## The ADMIN "full power" convention

ADMIN holds every permission because of one line of seed data, not because of code:

```sql
-- V2__seed_default_data.sql
INSERT INTO role_permissions (role_id, permission_id, ...)
SELECT 'ADMIN', id, ... FROM permissions;
```

When new permissions are added (see `V3__add_currency_and_statementformat_permissions.sql`, `V5__add_cross_user_transaction_permissions.sql`), each migration re-grants the new permission to ADMIN explicitly. **This is a maintenance convention, not an enforced invariant.** Any new permission migration must remember to grant to ADMIN or ADMIN silently becomes non-exhaustive.

## Invariants to preserve

1. **Role is the bundle.** Permissions are attached to roles, never to users. Two users with the same set of roles must resolve to the same effective permissions.
2. **Permission ids are the authority unit.** Downstream services check `hasPermission('transactions:delete')`, not `hasRole('ADMIN')`. This keeps servers from having to know the role→permission policy.
3. **Users can hold multiple roles.** Effective permissions are the union across roles. No code should assume a single role per user.
4. **No per-user permission overrides today.** The model supports only role-level grants. If exceptions are ever needed, see "Design space for future growth" below.

## UI authorization decisions

The guidance is borrowed from [bulletproof-react's authorization pattern](https://github.com/alan2207/bulletproof-react/blob/master/apps/react-vite/src/lib/authorization.tsx): **roles for layouts, permissions for actions.**

| UI concern | Check | Rationale |
|---|---|---|
| Route guards, admin sidebars, top-level navigation | Role (`hasRole('ADMIN')`) | Answers "who is this person structurally?" Layouts change rarely and are few. A role check reads naturally. |
| Buttons, menu items, enable/disable of action controls | Permission (`hasPermission('transactions:delete')`) | Answers "will the server accept this action?" The button is mirroring the server's own check. Self-documenting at the call site. |

Both checks read from the same `EffectivePermissions` payload — the service already returns `roles` and `permissions` together, so there is no extra round trip to pay.

### Why not use role checks for actions, given that roles == permission sets today?

The equivalence is coincidental, not structural, and will break silently the first time any of these happens:

- A third role is added that overlaps with ADMIN on some but not all permissions (e.g. `AUDITOR` with only `audit:read` + `users:read`). Any UI that wrote `hasRole('ADMIN')` to mean "can read audit logs" is now wrong.
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

### Explicitly rejected: snapshot-on-user-creation

An earlier idea was to snapshot a role's permissions into a `user_permissions` table at user creation time, then let operators refine that snapshot. **This is not the chosen approach.** Failure modes:

- **Permission drift.** New permissions added in later migrations (V3, V5) would never reach existing users, and there would be no way to distinguish "intentionally customized" snapshots from "just stale" ones.
- **Role names become meaningless.** If Alice and Bob both hold `ADMIN` but have different effective permissions, audit questions like "who can delete users?" can no longer be answered by querying `role_permissions`.
- **Source-of-truth ambiguity.** No clean answer to "is the snapshot the truth, or is it role + overrides?" Every operation has edge cases (what if the role later drops a permission the user snapshotted?).

If per-user exceptions are ever needed, use option 2 (additive delta) instead. It preserves role-as-bundle and avoids drift.

## Quick reference

- **Where permissions are defined:** `db/migration/V*.sql`
- **Where ADMIN is granted all permissions:** `db/migration/V2__seed_default_data.sql` (and each subsequent migration that adds a permission)
- **Resolver query:** `UserRoleRepository.findPermissionIdsByUserId`
- **DTO returned to downstream services:** `service/dto/EffectivePermissions.java` (fields: `roles`, `permissions`)
- **UI rule of thumb:** roles for layouts, permissions for actions
