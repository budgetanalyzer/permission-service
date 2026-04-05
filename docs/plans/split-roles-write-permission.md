# Plan: Split `roles:write` Into `roles:define` and `roles:assign`

Date: 2026-04-05

Status: Future

## Problem

`roles:write` currently guards two distinct concerns:

| Concern | Endpoints | Resource |
|---------|-----------|----------|
| Role definitions | `POST /v1/roles`, `PUT /v1/roles/{id}` | `roles` table |
| User-role assignments | `POST /v1/users/{id}/roles`, `DELETE /v1/users/{id}/roles/{roleId}` | `user_roles` table |

An ADMIN who needs to assign existing roles to users must also be able to create and modify role definitions. This violates least privilege.

## Proposed Change

Replace `roles:write` with two granular permissions:

- **`roles:define`** — create/update role definitions (`roles` table)
- **`roles:assign`** — assign/revoke roles on users (`user_roles` table)

### Role grants after the change

| Role | Role-related permissions |
|------|--------------------------|
| ADMIN | `roles:read`, `roles:assign` |
| SUPER_ADMIN (new) | `roles:read`, `roles:define`, `roles:assign`, `roles:delete` |

SYSTEM user gets the SUPER_ADMIN role. Real users can be elevated to SUPER_ADMIN when needed.

## Files to change

### Migration (new `V5__split_roles_write_permission.sql`)

```sql
-- Remove roles:write
DELETE FROM role_permissions WHERE permission_id = 'roles:write';
DELETE FROM permissions WHERE id = 'roles:write';

-- Add roles:define and roles:assign
INSERT INTO permissions (id, name, resource_type, action, created_at, created_by) VALUES
    ('roles:define', 'Create/Modify Role Definitions', 'role', 'define', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('roles:assign', 'Assign/Revoke Roles on Users', 'role', 'assign', CURRENT_TIMESTAMP, 'SYSTEM');

-- New SUPER_ADMIN role
INSERT INTO roles (id, name, description, is_system, created_at, created_by) VALUES
    ('SUPER_ADMIN', 'Super Administrator', 'Full role management including definitions and deletion', true, CURRENT_TIMESTAMP, 'SYSTEM');

-- SUPER_ADMIN gets all role permissions
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by) VALUES
    ('SUPER_ADMIN', 'roles:read', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('SUPER_ADMIN', 'roles:define', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('SUPER_ADMIN', 'roles:assign', CURRENT_TIMESTAMP, 'SYSTEM'),
    ('SUPER_ADMIN', 'roles:delete', CURRENT_TIMESTAMP, 'SYSTEM');

-- ADMIN gets read + assign (not define, not delete)
INSERT INTO role_permissions (role_id, permission_id, created_at, created_by) VALUES
    ('ADMIN', 'roles:assign', CURRENT_TIMESTAMP, 'SYSTEM');
-- ADMIN already has roles:read; roles:write was removed above

-- Assign SUPER_ADMIN to SYSTEM user
INSERT INTO user_roles (user_id, role_id, created_at, created_by) VALUES
    ('SYSTEM', 'SUPER_ADMIN', CURRENT_TIMESTAMP, 'SYSTEM');
```

### Controllers (2 files, 4 annotations)

**`RoleController.java`**
- Line 102: `POST /v1/roles` — change `roles:write` to `roles:define`
- Line 135: `PUT /v1/roles/{id}` — change `roles:write` to `roles:define`

**`UserPermissionController.java`**
- Line 136: `POST /v1/users/{id}/roles` — change `roles:write` to `roles:assign`
- Line 159: `DELETE /v1/users/{id}/roles/{roleId}` — change `roles:write` to `roles:assign`

### Tests (2 files, 9 test methods)

**`TestConstants.java`** (line 19 area)
- Remove `PERM_ROLES_WRITE`
- Add `PERM_ROLES_DEFINE = "roles:define"` and `PERM_ROLES_ASSIGN = "roles:assign"`

**`RoleControllerTest.java`** — 4 methods referencing `PERM_ROLES_WRITE`:
- Lines 159, 182: create role tests — use `PERM_ROLES_DEFINE`
- Lines 229, 251: update role tests — use `PERM_ROLES_DEFINE`

**`UserPermissionControllerTest.java`** — 5 methods referencing `PERM_ROLES_WRITE`:
- Lines 180, 204, 222: assign role tests — use `PERM_ROLES_ASSIGN`
- Lines 257, 287: revoke role tests — use `PERM_ROLES_ASSIGN`

### service-common

**`ClaimsHeaderTestBuilder.java`** (line 77 area)
- Replace `"roles:write"` with `"roles:define"`, `"roles:assign"` in `ADMIN_PERMISSIONS`

### Docs

**`permission-service/README.md`**
- Update endpoint auth column for the 4 affected endpoints
- Update default roles table to include SUPER_ADMIN
- Update permission count

**`permission-service/AGENTS.md`**
- Update any permission references

## Cross-service impact

No other services (`transaction-service`, `currency-service`, etc.) reference `roles:write` in code or tests. Impact is contained to `permission-service` and `service-common`.

## Verification

- ADMIN can assign/revoke roles but cannot create/update/delete role definitions
- SUPER_ADMIN can do all role operations
- SYSTEM user has SUPER_ADMIN role
- All existing tests updated and passing
- `ClaimsHeaderTestBuilder.admin()` reflects the new permission set
